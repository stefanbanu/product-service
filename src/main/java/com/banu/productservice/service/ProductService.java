package com.banu.productservice.service;

import com.banu.productservice.dto.ProductRequest;
import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.dto.VersionedProduct;
import com.banu.productservice.entity.Product;
import com.banu.productservice.exception.ProductNotFoundException;
import com.banu.productservice.exception.ProductVersionConflictException;
import com.banu.productservice.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Where the entity becomes a response, so the controller never sees a Product and the
 * repository never sees a DTO.
 *
 * <p>Constructor injection without {@code @Autowired}:
 * {@code @RequiredArgsConstructor} generates a constructor over the final fields, and a
 * single constructor is enough for Spring to use it. Note what that couples together - a
 * second constructor, or a non-final injected field, and Spring no longer has one
 * unambiguous candidate.
 *
 * <p>Methods that read or write a single product return {@link VersionedProduct}, because
 * the controller needs the row version to emit an ETag. The version never reaches the
 * response body.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    public List<ProductResponse> findAll() {
        return repository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Throws rather than returning an Optional or a null: the caller is a controller that
     * has no better answer than 404, and {@link com.banu.productservice.exception.ApiExceptionHandler}
     * is the single place that decides the exception means 404. Keeping that decision out of
     * here is why the exception is a plain RuntimeException and not a ResponseStatusException.
     */
    public VersionedProduct findById(Long id) {
        return versioned(load(id));
    }

    /**
     * Builds the entity from the request and lets the database and auditing fill in the
     * rest: id stays null so Spring Data JDBC issues an INSERT and reads the identity value
     * back, and createdAt stays null so {@code @CreatedDate} can set it. Assigning either
     * here would turn the save into an UPDATE that matches no row, or overwrite the
     * timestamp the column is meant to own.
     *
     * <p>version stays null too, which is now what marks the instance as new - see the field
     * on {@link Product}. Spring Data assigns the first version on insert.
     */
    public VersionedProduct create(ProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setPrice(request.price());

        return versioned(repository.save(product));
    }

    /**
     * Replaces the product, but only if it still looks the way the client last read it.
     *
     * <p>{@code expectedVersion} is the number from the client's {@code If-Match}; null means
     * {@code If-Match: *}, which asserts only that the row exists. Writing the expected
     * version onto the loaded entity is what arms the check: Spring Data then issues
     * {@code UPDATE ... WHERE id = ? AND version = ?}, so a stale value matches no row and
     * the database - not this code - is what detects the conflict. Comparing the two versions
     * here instead would be a check-then-act with a window between the read and the write.
     *
     * <p>The row is still loaded first, because the loaded instance carries the original
     * createdAt that the UPDATE writes back unchanged, and because a missing row has to be a
     * 404 rather than a conflict.
     *
     * <p>No PATCH semantics: every updatable field is required, because {@link ProductRequest}
     * marks both as mandatory.
     */
    public VersionedProduct update(Long id, Long expectedVersion, ProductRequest request) {
        Product product = load(id);

        product.setName(request.name());
        product.setPrice(request.price());
        if (expectedVersion != null) {
            product.setVersion(expectedVersion);
        }

        try {
            return versioned(repository.save(product));
        } catch (OptimisticLockingFailureException exception) {
            throw new ProductVersionConflictException(id);
        }
    }

    /**
     * Deletes the product, optionally only at a known version.
     *
     * <p>A null {@code expectedVersion} - no {@code If-Match}, or {@code *} - deletes
     * unconditionally, which is why the existence check is still needed: deleting a missing
     * row is a no-op in Spring Data JDBC, with nothing to distinguish "deleted" from "was
     * never there". Unlike PUT, an unconditional DELETE is permitted here: removing a row
     * someone else just edited discards their edit either way, and requiring a prior GET to
     * delete is friction the 404-on-replay behaviour already covers.
     */
    public void delete(Long id, Long expectedVersion) {
        Product product = load(id);

        if (expectedVersion == null) {
            repository.deleteById(id);
            return;
        }

        product.setVersion(expectedVersion);

        try {
            repository.delete(product);
        } catch (OptimisticLockingFailureException exception) {
            throw new ProductVersionConflictException(id);
        }
    }

    private Product load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    private VersionedProduct versioned(Product product) {
        return new VersionedProduct(ProductResponse.from(product), product.getVersion());
    }
}

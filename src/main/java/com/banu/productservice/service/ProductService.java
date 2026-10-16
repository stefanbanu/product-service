package com.banu.productservice.service;

import com.banu.productservice.dto.ProductRequest;
import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.entity.Product;
import com.banu.productservice.exception.ProductNotFoundException;
import com.banu.productservice.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    public ProductResponse findById(Long id) {
        return repository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Builds the entity from the request and lets the database and auditing fill in the
     * rest: id stays null so Spring Data JDBC issues an INSERT and reads the identity value
     * back, and createdAt stays null so {@code @CreatedDate} can set it. Assigning either
     * here would turn the save into an UPDATE that matches no row, or overwrite the
     * timestamp the column is meant to own.
     */
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setPrice(request.price());

        return ProductResponse.from(repository.save(product));
    }

    /**
     * Loads the row first rather than building a fresh Product with the id set. Two reasons:
     * a Product with a non-null id makes Spring Data JDBC issue an UPDATE, which for a
     * missing row fails deep in the data access layer instead of as a clean 404; and the
     * loaded instance carries the original createdAt, which the UPDATE then writes back
     * unchanged. Constructing the entity by hand would store a null there and violate the
     * column's NOT NULL constraint - auditing would not save it, because {@code @CreatedDate}
     * only fires for new instances.
     *
     * <p>Full replacement, not a patch: every updatable field is required, because
     * {@link ProductRequest} marks both as mandatory. Partial updates would need their own
     * DTO with nullable components and a way to distinguish absent from null.
     *
     * <p>No optimistic locking - the table has no version column, so two concurrent updates
     * are last-write-wins. Adding {@code @Version} means a changeSet for the column.
     */
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setName(request.name());
        product.setPrice(request.price());

        return ProductResponse.from(repository.save(product));
    }

    /**
     * {@code existsById} before {@code deleteById} because deleting a missing row is a no-op
     * in Spring Data JDBC - no exception, nothing to distinguish "deleted" from "was never
     * there". The check is what lets an unknown id be a 404 like it is everywhere else here.
     * It costs a second query, and it is a check-then-act: two concurrent deletes of the same
     * id can both pass the check, and the loser's delete quietly affects no rows.
     */
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }

        repository.deleteById(id);
    }
}

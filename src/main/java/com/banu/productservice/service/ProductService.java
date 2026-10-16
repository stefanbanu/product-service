package com.banu.productservice.service;

import com.banu.productservice.dto.ProductRequest;
import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.entity.Product;
import com.banu.productservice.exception.ProductNotFoundException;
import com.banu.productservice.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Where the entity becomes a response, so the controller never sees a Product and the
 * repository never sees a DTO.
 *
 * <p>Constructor injection without {@code @Autowired}: a single constructor is enough for
 * Spring to use it, and it keeps the field final.
 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

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
}

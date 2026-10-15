package com.banu.productservice.exception;

/**
 * A plain RuntimeException, not a ResponseStatusException, so the service layer carries no
 * HTTP concerns; {@link ApiExceptionHandler} is the single place that decides it means 404.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("No product with id " + id);
    }
}

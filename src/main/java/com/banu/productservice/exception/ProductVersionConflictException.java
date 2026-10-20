package com.banu.productservice.exception;

/**
 * The product changed between the client's read and its write: the {@code If-Match} it sent
 * no longer matches the row's version. A plain RuntimeException like
 * {@link ProductNotFoundException}, so the service layer carries no HTTP concerns;
 * {@link ApiExceptionHandler} is the single place that decides it means 412.
 */
public class ProductVersionConflictException extends RuntimeException {

    public ProductVersionConflictException(Long id) {
        super("Product " + id + " was modified by someone else; re-fetch it and retry");
    }
}

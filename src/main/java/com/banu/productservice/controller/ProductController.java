package com.banu.productservice.controller;

import com.banu.productservice.dto.ProductRequest;
import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Lists the product table - on a fresh boot, the ten rows seeded by the liquibase module's
 * 002-insert-products.sql - and creates new rows in it.
 *
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /**
     * No try/catch and no Optional handling: an unknown id makes the service throw
     * ProductNotFoundException, and
     * {@link com.banu.productservice.exception.ApiExceptionHandler} turns that into the 404
     * ProblemDetail. A non-numeric id never reaches the service - Spring fails to bind it
     * and answers 400 on its own.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    /**
     * {@code @Valid} is load-bearing: drop it and every constraint on
     * {@link ProductRequest} goes quiet, so a blank name or a negative price is accepted and
     * fails later as a 500 from the database instead of a 400 naming the field.
     * {@link com.banu.productservice.exception.ApiExceptionHandler} turns the resulting
     * MethodArgumentNotValidException into a ProblemDetail listing every bad field.
     *
     * <p>201 with the created resource as the body, so a client learns the assigned id and
     * createdAt without a follow-up request, plus a Location header pointing at
     * {@link #findById}.
     *
     * <p>The URI is built from the current request rather than concatenated from a literal
     * "/products/", so it stays correct if this controller's @RequestMapping ever moves or
     * the app gains a context path. It is absolute, as the Location header is specified to
     * be; behind a reverse proxy that needs server.forward-headers-strategy set, or the host
     * in it will be the internal one.
     */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = service.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}

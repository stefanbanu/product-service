package com.banu.productservice.controller;

import com.banu.productservice.dto.ProductRequest;
import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Lists the product table - on a fresh boot, the ten rows seeded by the liquibase module's
 * 002-insert-products.sql - fetches a single row, and creates, replaces and removes rows
 * in it.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

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

    /**
     * Replaces the product's updatable fields, reusing {@link ProductRequest} because what a
     * client may set is the same on create and update - and so is the reason id and
     * createdAt are missing from it. Both fields are therefore mandatory here too: PUT
     * replaces, so omitting price is a 400 rather than "leave it alone".
     *
     * <p>An unknown id is a 404, not an insert at that id. PUT is allowed to create the
     * resource at the target URI, but ids here come from the identity column, and writing a
     * client-chosen one does not advance that sequence - the next generated id would collide
     * with it.
     *
     * <p>200 with the stored result rather than 204, so a client sees what the row actually
     * looks like now, including the createdAt it did not send.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    /**
     * 204 with no body: there is nothing left to describe, and a deleted product is not
     * something a client should get a representation of.
     *
     * <p>An unknown id is a 404, consistent with the other two single-product endpoints.
     * That makes DELETE not quite idempotent in its status code - the first call answers
     * 204, a repeat answers 404 - which is a deliberate trade. Answering 204 unconditionally
     * would keep the status identical on replay but would also report success for a typo'd
     * id. The effect on the server is idempotent either way: the row is gone and stays gone.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);

        return ResponseEntity.noContent().build();
    }
}

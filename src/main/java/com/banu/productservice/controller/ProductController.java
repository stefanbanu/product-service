package com.banu.productservice.controller;

import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.service.ProductService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint this skeleton ships with: a 200 listing whatever is in the product
 * table, which on a fresh boot is the ten rows seeded by the liquibase module's
 * 002-insert-products.sql.
 *
 * <p>When POST arrives, give it its own request DTO without id or createdAt -- the absence
 * of those fields is what makes them unsettable by a client -- and annotate the parameter
 * {@code @Valid}, or the DTO's constraints are silently inert and every payload is
 * accepted.
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
}

package com.banu.productservice.dto;

import com.banu.productservice.entity.Product;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The trust boundary: what reaches the wire is decided by this record's components rather
 * than by whatever fields Product later grows.
 *
 * <p>A static factory so it is usable as a method reference ({@code ProductResponse::from}).
 * New components are appended at the end rather than slotted in next to related ones,
 * because callers construct the record positionally and inserting in the middle silently
 * shifts every one of them.
 */
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Instant createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCreatedAt());
    }
}

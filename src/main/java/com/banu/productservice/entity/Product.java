package com.banu.productservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plain object, not a JPA @Entity: this service has no database, and
 * {@link com.banu.productservice.repository.InMemoryProductRepository} keeps instances in a
 * map. It sits in entity/ rather than dto/ because it is the domain model the service works
 * with, and the DTOs are what cross the wire.
 *
 * <p>Deliberately {@code @Getter @Setter @NoArgsConstructor} rather than {@code @Data}, as
 * in the sibling order-service: {@code @Data} would generate equals/hashCode over all
 * fields including the generated id, which misbehaves for unsaved instances and in
 * collections.
 *
 * <p>Money is {@link BigDecimal}, never a float type.
 */
@Getter
@Setter
@NoArgsConstructor
public class Product {

    private Long id;

    private String name;

    private BigDecimal price;

    private Instant createdAt;
}

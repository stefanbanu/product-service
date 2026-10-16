package com.banu.productservice.entity;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The Spring Data JDBC aggregate root behind the {@code product} table. Not a JPA
 * {@code @Entity}: there is no Hibernate on the classpath, so {@code @Id} and
 * {@code @Table} here are the Spring Data annotations, and nothing generates DDL - the
 * table is created by db/changelog in the liquibase module and this class has to match it.
 *
 * <p>It stays in entity/ rather than dto/ because it is the domain model the service works
 * with, and the DTOs are what cross the wire.
 *
 * <p>Column names come from the default naming strategy, which snake_cases the property
 * name, so {@code createdAt} maps to {@code created_at} with no annotation needed.
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
@Table("product")
public class Product {

    /**
     * Null means unsaved: Spring Data JDBC decides INSERT versus UPDATE purely by whether
     * this is null, and fills it from the identity column after an insert. Setting it by
     * hand on a new instance turns the next save into an UPDATE that matches no row.
     */
    @Id
    private Long id;

    private String name;

    private BigDecimal price;

    /**
     * Populated on first insert by auditing, which
     * {@link com.banu.productservice.config.JdbcAuditingConfiguration} enables; without
     * that class this stays null and the insert fails on the column's NOT NULL constraint.
     */
    @CreatedDate
    private Instant createdAt;
}

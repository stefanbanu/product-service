package com.banu.productservice.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * What a client is allowed to send when creating a product. It has no id and no createdAt
 * component, and that absence is the whole point: both are assigned server-side - the id by
 * the identity column, createdAt by auditing - and a client cannot set what it cannot name.
 * Reusing {@link ProductResponse} here would hand both fields back to the caller.
 *
 * <p>The constraints mirror the column definitions in 001-create-product-table.sql rather
 * than guessing at reasonable limits. Without {@code @Size} a 300-character name reaches
 * VARCHAR(255) and fails as a 500; with it the caller gets a 400 naming the field. Same for
 * {@code @Digits} against DECIMAL(19, 2) - 17 integer digits plus 2 fractional ones - which
 * otherwise silently rounds a third decimal place away.
 *
 * <p>Constraints here are inert unless the controller parameter is annotated
 * {@code @Valid}.
 */
public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price) {
}

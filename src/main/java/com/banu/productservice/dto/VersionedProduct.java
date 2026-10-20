package com.banu.productservice.dto;

/**
 * A response plus the row version it was read at, so the controller can turn the version
 * into an ETag header.
 *
 * <p>The pairing exists so {@link ProductResponse} does not have to grow a {@code version}
 * component. The version is transport metadata - a concurrency token - not part of what a
 * product is, and HTTP already has a header for exactly that.
 */
public record VersionedProduct(ProductResponse product, long version) {
}

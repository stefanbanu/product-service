package com.banu.productservice.repository;

import com.banu.productservice.entity.Product;
import org.springframework.data.repository.ListCrudRepository;

/**
 * The seam that keeps the storage choice out of the service. Spring Data JDBC generates the
 * implementation at runtime; there is no class in this package implementing it any more.
 *
 * <p>{@link ListCrudRepository} rather than {@code CrudRepository}: the latter's
 * {@code findAll()} returns {@link Iterable}, which has no {@code stream()}, so
 * {@link com.banu.productservice.service.ProductService} would stop compiling. This variant
 * returns a {@link java.util.List} and keeps the three methods the service and controller
 * already use - {@code save}, {@code findById}, {@code findAll} - with the same signatures
 * they had when this was a hand-written interface.
 *
 * <p>Query methods are fine to add here, but remember Spring Data JDBC has no lazy loading
 * and no dirty tracking: a {@code findBy...} returns detached objects, and changing one does
 * nothing until it is passed back to {@code save}.
 */
public interface ProductRepository extends ListCrudRepository<Product, Long> {
}

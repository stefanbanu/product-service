package com.banu.productservice.repository;

import com.banu.productservice.entity.Product;
import java.util.List;
import java.util.Optional;

/**
 * The seam that keeps the storage choice out of the service. The only implementation today
 * is {@link InMemoryProductRepository}; moving to a real database means adding a starter, a
 * driver and an implementation of this interface, and leaving
 * {@link com.banu.productservice.service.ProductService} alone.
 *
 * <p>Modelled on the subset of Spring Data's CrudRepository this service actually needs, so
 * that an eventual {@code extends JpaRepository<Product, Long>} is a drop-in rather than a
 * rename of every call site.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();
}

package com.banu.productservice.repository;

import com.banu.productservice.entity.Product;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * Process-local storage: everything here is lost on restart, and two instances of this
 * service would not see each other's products. That is the deliberate trade for a service
 * that builds, runs and tests with no database to install.
 *
 * <p>A ConcurrentHashMap keyed by id, with an AtomicLong standing in for the identity
 * column, so ids start at 1 and never repeat within a run.
 */
@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();

    private final AtomicLong nextId = new AtomicLong(1);

    /**
     * Assigns the id and the creation timestamp on first save, the way a database and
     * {@code @CreationTimestamp} would, and leaves both alone on an update.
     */
    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(nextId.getAndIncrement());
            product.setCreatedAt(Instant.now());
        }
        products.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }

    /** Ordered by id so the listing is stable; the map itself gives no ordering. */
    @Override
    public List<Product> findAll() {
        return products.values().stream()
                .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                .toList();
    }
}

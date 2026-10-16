package com.banu.productservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.banu.productservice.entity.Product;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the wiring between the three moving parts rather than the repository's own logic,
 * which Spring Data generates: that the changelog from the liquibase module is on this
 * app's classpath and ran, that {@link Product} maps onto the table it created, and that an
 * insert satisfies the column constraints that changelog declares.
 *
 * <p>{@code @SpringBootTest} rather than {@code @DataJdbcTest} on purpose - the point is
 * that the real application context, with its real datasource and Liquibase
 * auto-configuration, produces a queryable table.
 */
@SpringBootTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    /**
     * The seeded rows from 002-insert-products.sql. An exact count, which is why
     * {@link #saveAssignsIdAndCreatedAt()} has to roll its insert back.
     */
    @Test
    void findAllReturnsTheSeededProducts() {
        List<Product> products = repository.findAll();

        assertThat(products).hasSize(10);
        assertThat(products)
                .extracting(Product::getName)
                .contains("Mechanical Keyboard", "Surge-Protected Power Strip");
        assertThat(products)
                .allSatisfy(
                        product -> {
                            assertThat(product.getId()).isNotNull();
                            assertThat(product.getPrice()).isNotNull();
                            assertThat(product.getCreatedAt()).isNotNull();
                        });
    }

    /**
     * Guards the one piece of this wiring with no compile-time safety net:
     * {@code @EnableJdbcAuditing} is what populates createdAt, and the column is NOT NULL,
     * so dropping that annotation turns this into a constraint violation rather than a
     * silently null field.
     *
     * <p>{@code @Transactional} rolls the insert back - the in-memory database is shared
     * across this class's tests, and an extra row would break the count above.
     */
    @Test
    @Transactional
    void saveAssignsIdAndCreatedAt() {
        Product product = new Product();
        product.setName("Trackball");
        product.setPrice(new BigDecimal("59.95"));

        Product saved = repository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(repository.findById(saved.getId()))
                .get()
                .satisfies(
                        found -> {
                            assertThat(found.getName()).isEqualTo("Trackball");
                            assertThat(found.getPrice()).isEqualByComparingTo("59.95");
                        });
    }
}

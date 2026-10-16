package com.banu.productservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

/**
 * Switches on the auditing that fills {@code @CreatedDate} fields. Spring Data JDBC does
 * not audit by default, and {@code created_at} is NOT NULL in the changelog, so without
 * this every insert fails with 'NULL not allowed for column "CREATED_AT"' rather than
 * quietly storing a null.
 *
 * <p>Its own class rather than another annotation on
 * {@link com.banu.productservice.ProductServiceApplication}, which stays a bare
 * {@code @SpringBootApplication} - persistence configuration is not the entry point's
 * business, and this is the file to add a {@code @Bean} to when auditing later needs an
 * {@code AuditorAware} or a fixed clock.
 */
@Configuration
@EnableJdbcAuditing
public class JdbcAuditingConfiguration {
}

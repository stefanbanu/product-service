--liquibase formatted sql

-- The optimistic locking counter behind the ETag / If-Match flow on PUT. Every update
-- carries "WHERE id = ? AND version = ?" and bumps the value, so an update built from a
-- stale read matches no row and fails loudly instead of silently overwriting the change it
-- never saw.
--
-- DEFAULT 1 rather than 0, and this is load-bearing: Spring Data JDBC decides INSERT versus
-- UPDATE from the version property once one exists, and treats null OR ZERO as "new". Rows
-- backfilled with 0 would be taken for unsaved objects, so the first update of a seeded row
-- would be issued as an INSERT and collide on the primary key.

--changeset stefanbanu:003-add-product-version
ALTER TABLE product ADD COLUMN version BIGINT DEFAULT 1 NOT NULL;
--rollback ALTER TABLE product DROP COLUMN version;

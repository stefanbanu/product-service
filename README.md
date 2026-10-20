# product-service

A small Spring Boot CRUD API over a `product` table, used as the backend for
[product-ui](https://github.com/stefanbanu/product-ui).

Spring Boot 4.1.1 · Java 25 · Spring Data JDBC · H2 · Liquibase 5.0.4

## Running it

```bash
./mvnw spring-boot:run          # :8081
```

Port 8081, not 8080 — that one belongs to the sibling order-service, and both should be able
to run at once.

The database is **in-memory**: it is created empty on every boot, Liquibase then creates the
schema and seeds ten products. Nothing written through the API survives a restart. That is
deliberate — the service builds, runs and tests with no database to install.

## The API

| Method | Path | Success | Failures |
|---|---|---|---|
| `GET` | `/products` | `200` array | — |
| `GET` | `/products/{id}` | `200` | `404` unknown id · `400` unbindable id |
| `POST` | `/products` | `201` + `Location` + body | `400` with a per-field `errors` map |
| `PUT` | `/products/{id}` | `200` | `404` · `400` |
| `DELETE` | `/products/{id}` | `204` empty | `404` |

```bash
curl localhost:8081/products
curl -X POST localhost:8081/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Trackball","price":59.95}'
```

A product is `{id, name, price, createdAt}`. `id` and `createdAt` are assigned server-side
and are not components of the request DTO, which is what makes them unsettable by a client.

Errors are RFC 7807 `ProblemDetail` bodies. A validation failure reports **every** bad field
at once:

```json
{ "title": "Invalid request", "status": 400, "detail": "Request validation failed",
  "errors": { "name": "must not be blank", "price": "must be greater than 0" } }
```

Three decisions worth knowing before you file a bug against them:

- **`PUT` is a full replacement.** Omitting `price` is a `400`, not "leave it alone". It
  preserves `createdAt`, and an unknown id is a `404` rather than an insert at that id —
  ids come from the identity column, and writing a client-chosen one would not advance the
  sequence.
- **`DELETE` answers `404` on replay.** The first call is `204`, a repeat is `404`. The
  server-side effect is idempotent either way; answering `204` unconditionally would report
  success for a typo'd id.
- **No optimistic locking.** No version column, so two concurrent updates are
  last-write-wins. Adding `@Version` means a changeSet for the column.

## Looking inside the database

The H2 console is enabled for local development at **http://localhost:8081/h2-console**.

Replace the login form's default `jdbc:h2:~/test` with the real URL, or H2 will silently
open a different, empty database — which looks exactly like "my data vanished":

```
JDBC URL:  jdbc:h2:mem:productdb
User:      sa
Password:  (empty)
```

## The liquibase module

`liquibase/` is a **standalone Maven project**, not a `<module>` of this pom — this one is
packaging `jar` and cannot aggregate. It owns the schema; nothing generates DDL.

```
liquibase/
  pom.xml
  liquibase.properties                 url, credentials, changeLogFile
  src/main/resources/db/changelog/
    db.changelog-master.xml            includeAll over changes/
    changes/001-create-product-table.sql
    changes/002-insert-products.sql
```

The app reads those changelogs from its own classpath — a `maven-resources-plugin` execution
copies the tree into `target/classes` at build time, so a single `./mvnw spring-boot:run`
works from a fresh clone and a deployed jar carries its own migrations.

To run migrations by hand, against a scratch file database in `~/.h2/product-service/`:

```bash
./mvnw -f liquibase/pom.xml liquibase:update
./mvnw -f liquibase/pom.xml liquibase:status
./mvnw -f liquibase/pom.xml liquibase:dropAll      # mvn clean does NOT reset it
./mvnw -f liquibase/pom.xml liquibase:update -Dliquibase.url=jdbc:h2:file:/tmp/other
```

That scratch database is **not** the one the app uses; the app's is in-memory and rebuilt
every boot. Running `liquibase:update` by hand does not change what the API serves.

### Adding a changeSet

Drop a new `NNN-`prefixed file into `changes/` — `includeAll` orders by filename, so append
rather than renumber, or already-applied checksums stop matching. Formatted SQL needs an
explicit `--rollback` line, since Liquibase cannot infer the inverse the way it can for XML.
The SQL is H2-specific.

## Things that will bite you

- **Spring Data JDBC, not JPA.** No Hibernate, no schema generation. Add a field to
  `Product` without a changeSet and the column simply does not exist.
- **`CASE_INSENSITIVE_IDENTIFIERS=TRUE`** in the datasource URL is load-bearing. The
  changelog's unquoted `CREATE TABLE product` folds to `PRODUCT`, while Spring Data quotes
  everything it emits (`FROM "product"`); without the flag every query fails with
  `Table "product" not found`.
- **`DB_CLOSE_DELAY=-1`** likewise: H2 discards an in-memory database when the last
  connection closes, which happens between Liquibase finishing and the first request.
- **`JdbcAuditingConfiguration`** is what fills `@CreatedDate`. Delete it and every insert
  fails on the `NOT NULL` constraint on `created_at`.
- **Boot 4 split the starters.** `spring-boot-starter-webmvc` is the old `-web`, each slice
  has its own `-test` starter, and auto-configuration lives in per-technology modules —
  hence the explicit `spring-boot-liquibase` and `spring-boot-h2console` dependencies.

## Tests

```bash
./mvnw test
```

19 tests. The controller and repository tests run against the real context, datasource and
Liquibase-created table rather than mocks — the wiring is the part worth covering. They are
`@Transactional` and roll back, because they share one in-memory database and the repository
test asserts an exact row count.

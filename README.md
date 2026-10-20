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
| `GET` | `/products/{id}` | `200` + `ETag` | `404` unknown id · `400` unbindable id |
| `POST` | `/products` | `201` + `Location` + `ETag` + body | `400` with a per-field `errors` map |
| `PUT` | `/products/{id}` | `200` + new `ETag` | `404` · `400` · `412` stale · `428` no `If-Match` |
| `DELETE` | `/products/{id}` | `204` empty | `404` · `412` if a stale `If-Match` is sent |

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
- **`PUT` requires `If-Match`.** See below. An unconditional update is refused with `428`.

## Concurrent edits: ETag and If-Match

Every single-product response carries an `ETag` holding the row's version, and `PUT`
requires it back in `If-Match`. Without that, two people editing the same product would
both get `200` and the first edit would vanish silently — `PUT` is a full replacement, so
the second writer sends back the stale fields it read.

```bash
# read, and keep the ETag
curl -i localhost:8081/products/5 | grep -i etag          # ETag: "2"

# write, asserting nothing has changed since
curl -i -X PUT localhost:8081/products/5 -H 'If-Match: "2"' \
  -H 'Content-Type: application/json' -d '{"name":"Headset","price":249.00}'
                                                           # 200, ETag: "3"

# anyone still holding "2" now gets refused
curl -X PUT localhost:8081/products/5 -H 'If-Match: "2"' \
  -H 'Content-Type: application/json' -d '{"name":"Headset Pro","price":279.00}'
                                                           # 412 Precondition failed
```

| Situation | Status |
|---|---|
| `If-Match` matches the current version | `200` + the next `ETag` |
| `If-Match` is stale | `412 Precondition Failed` |
| No `If-Match` at all | `428 Precondition Required` |
| `If-Match: *` | asserts only that the row exists |

`412` rather than `409` because the client stated its expectation as an HTTP precondition;
`409` would be right if the version travelled in the request body. The version is **not** a
field on `ProductResponse` — it is transport metadata, and HTTP already has a header for it.

`DELETE` honours `If-Match` when sent but does not require it.

### How it works: `@Version`

The ETag is a `version` column, maintained by Spring Data JDBC through one annotated field:

```java
// entity/Product.java
@Version
private Long version;                 // org.springframework.data.annotation.Version
```

With that present, every update Spring Data issues becomes conditional and self-incrementing:

```sql
UPDATE product SET name = ?, price = ?, version = version + 1
 WHERE id = ? AND version = ?         -- the version the client asserted
```

**The enforcement is the database's, not this code's.** `ProductService.update` writes the
client's expected version onto the loaded entity before saving, which is what arms that
`WHERE` clause. A stale value matches no row, zero rows affected becomes
`OptimisticLockingFailureException`, and the service rethrows it as
`ProductVersionConflictException` so `ApiExceptionHandler` can answer `412`. Comparing the
two versions in Java instead would be a check-then-act with a window between the read and
the write — exactly the race being closed.

The round trip, for one product:

```
GET  /products/5   → ETag: "2"     version column = 2
PUT  /products/5     If-Match: "2" → WHERE version = 2 matches → version becomes 3, ETag: "3"
PUT  /products/5     If-Match: "2" → WHERE version = 2 matches nothing → 412
```

Three consequences worth knowing before touching this:

- **`@Version` changes how new-ness is decided.** Without it, Spring Data treats "id is
  null" as a new aggregate. With it, the *version* becomes the signal — and **null or zero**
  both mean new. This is why `create()` leaves version null, and why nothing should ever set
  it to `0`.
- **That is also why `003-add-product-version.sql` backfills with `1`, not `0`.** Rows
  defaulted to zero would be taken for unsaved objects, so the first update of a seeded row
  would be issued as an `INSERT` and collide on the primary key.
- **The field is not on `ProductResponse`.** `VersionedProduct` pairs a response with the
  version it was read at, purely so the controller can build the ETag header. Adding
  `version` to the DTO would put a bookkeeping integer into the API's data model, which is
  the thing the ETag approach exists to avoid.

Adding `@Version` to another aggregate later means the same three things: a changeSet for
the column defaulting to 1, the annotated field, and an `If-Match` contract on its writes.

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
- **`@Version` on `Product` redefines "new".** Null *or zero* makes Spring Data issue an
  `INSERT` instead of an `UPDATE`, so a row whose version is 0 fails on a duplicate primary
  key the first time anyone edits it. Never default the column to 0.
- **Boot 4 split the starters.** `spring-boot-starter-webmvc` is the old `-web`, each slice
  has its own `-test` starter, and auto-configuration lives in per-technology modules —
  hence the explicit `spring-boot-liquibase` and `spring-boot-h2console` dependencies.

## Tests

```bash
./mvnw test
```

24 tests. The controller and repository tests run against the real context, datasource and
Liquibase-created table rather than mocks — the wiring is the part worth covering. They are
`@Transactional` and roll back, because they share one in-memory database and the repository
test asserts an exact row count.

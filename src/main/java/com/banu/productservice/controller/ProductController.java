package com.banu.productservice.controller;

import com.banu.productservice.dto.ProductRequest;
import com.banu.productservice.dto.ProductResponse;
import com.banu.productservice.dto.VersionedProduct;
import com.banu.productservice.exception.IfMatchRequiredException;
import com.banu.productservice.exception.ProductVersionConflictException;
import com.banu.productservice.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Lists the product table - on a fresh boot, the ten rows seeded by the liquibase module's
 * 002-insert-products.sql - fetches a single row, and creates, replaces and removes rows
 * in it.
 *
 * <p>Every single-product response carries an ETag holding the row's version, and PUT
 * requires that ETag back in an {@code If-Match} header. That is the whole concurrency
 * story: a client may only replace a product it has actually read, and only while nobody
 * else has changed it since. The version travels as a header rather than in the body so the
 * API's data model stays free of bookkeeping fields.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /**
     * No try/catch and no Optional handling: an unknown id makes the service throw
     * ProductNotFoundException, and
     * {@link com.banu.productservice.exception.ApiExceptionHandler} turns that into the 404
     * ProblemDetail. A non-numeric id never reaches the service - Spring fails to bind it
     * and answers 400 on its own.
     *
     * <p>This is also where a client gets the ETag it will need to update the product, so
     * "read before write" is not a convention here but a mechanism.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        VersionedProduct found = service.findById(id);

        return ResponseEntity.ok().eTag(etag(found)).body(found.product());
    }

    /**
     * {@code @Valid} is load-bearing: drop it and every constraint on
     * {@link ProductRequest} goes quiet, so a blank name or a negative price is accepted and
     * fails later as a 500 from the database instead of a 400 naming the field.
     * {@link com.banu.productservice.exception.ApiExceptionHandler} turns the resulting
     * MethodArgumentNotValidException into a ProblemDetail listing every bad field.
     *
     * <p>201 with the created resource as the body, so a client learns the assigned id and
     * createdAt without a follow-up request, plus a Location header pointing at
     * {@link #findById} and the ETag of the version just written - which means a client can
     * create and then immediately update without an intervening GET.
     *
     * <p>The URI is built from the current request rather than concatenated from a literal
     * "/products/", so it stays correct if this controller's @RequestMapping ever moves or
     * the app gains a context path. It is absolute, as the Location header is specified to
     * be; behind a reverse proxy that needs server.forward-headers-strategy set, or the host
     * in it will be the internal one.
     */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        VersionedProduct created = service.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.product().id())
                .toUri();

        return ResponseEntity.created(location).eTag(etag(created)).body(created.product());
    }

    /**
     * Replaces the product's updatable fields, reusing {@link ProductRequest} because what a
     * client may set is the same on create and update - and so is the reason id and
     * createdAt are missing from it. Both fields are therefore mandatory here too: PUT
     * replaces, so omitting price is a 400 rather than "leave it alone".
     *
     * <p>{@code If-Match} is mandatory. Without it the request is an unconditional
     * overwrite, which is the exact failure optimistic locking exists to prevent, so a
     * missing header is a 428 rather than a silent success. A stale one is a 412. The
     * response carries the new ETag, so a client can make a second edit without re-reading.
     *
     * <p>An unknown id is a 404, not an insert at that id. PUT is allowed to create the
     * resource at the target URI, but ids here come from the identity column, and writing a
     * client-chosen one does not advance that sequence - the next generated id would collide
     * with it.
     *
     * <p>200 with the stored result rather than 204, so a client sees what the row actually
     * looks like now, including the createdAt it did not send.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ProductRequest request) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new IfMatchRequiredException();
        }

        VersionedProduct updated = service.update(id, expectedVersion(ifMatch, id), request);

        return ResponseEntity.ok().eTag(etag(updated)).body(updated.product());
    }

    /**
     * 204 with no body: there is nothing left to describe, and a deleted product is not
     * something a client should get a representation of.
     *
     * <p>{@code If-Match} is honoured when sent but not required, unlike PUT - see the
     * service for why. Sending a stale one is a 412.
     *
     * <p>An unknown id is a 404, consistent with the other two single-product endpoints.
     * That makes DELETE not quite idempotent in its status code - the first call answers
     * 204, a repeat answers 404 - which is a deliberate trade. Answering 204 unconditionally
     * would keep the status identical on replay but would also report success for a typo'd
     * id. The effect on the server is idempotent either way: the row is gone and stays gone.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Long expected = ifMatch == null || ifMatch.isBlank() ? null : expectedVersion(ifMatch, id);

        service.delete(id, expected);

        return ResponseEntity.noContent().build();
    }

    /** Quoted, as an entity-tag has to be on the wire: a bare number is not a valid ETag. */
    private String etag(VersionedProduct versioned) {
        return "\"" + versioned.version() + "\"";
    }

    /**
     * Turns the received header into the version to assert on. {@code *} means "any existing
     * representation" and becomes null, which the service reads as an existence-only check.
     *
     * <p>Tolerates the forms a real client or proxy may send: quotes, and the {@code W/}
     * weak-validator prefix. Anything else is a precondition that cannot possibly hold, so
     * it is reported as a failed precondition rather than a bad request - the client did
     * send an If-Match, it just does not match anything this server could have issued.
     */
    private Long expectedVersion(String ifMatch, Long id) {
        String value = ifMatch.trim();

        if ("*".equals(value)) {
            return null;
        }

        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }

        value = value.replace("\"", "");

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ProductVersionConflictException(id);
        }
    }
}

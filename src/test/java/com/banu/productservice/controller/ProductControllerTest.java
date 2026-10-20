package com.banu.productservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Boot 4 package: the Boot 3 home was org.springframework.boot.test.autoconfigure.web.servlet.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives every /products endpoint through the real stack - validation, the service, Spring
 * Data JDBC, the Liquibase-created table - rather than a mocked service, because the parts
 * worth covering here are exactly the ones a mock would hide: that {@code @Valid} is wired,
 * that the identity column and auditing fill in id and createdAt, and that a write is
 * visible to the next read.
 *
 * <p>{@code @Transactional} rolls every test back. The in-memory database is shared with
 * {@link com.banu.productservice.repository.ProductRepositoryTest}, which asserts an exact
 * row count, so a committed insert here would break that test depending on execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createReturns201WithTheAssignedIdAndTimestamp() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Trackball\", \"price\": 59.95}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Trackball"))
                .andExpect(jsonPath("$.price").value(59.95))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    /**
     * The Location header is only worth having if it resolves, so this follows it instead of
     * pattern-matching the string: whatever URI the POST advertised is fetched, and has to
     * answer 200 with the product that was just created.
     */
    @Test
    void createAdvertisesAFetchableLocation() throws Exception {
        String location = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Trackball\", \"price\": 59.95}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(get(URI.create(location).getPath()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trackball"))
                .andExpect(jsonPath("$.price").value(59.95));
    }

    @Test
    void findByIdReturnsASeededProduct() throws Exception {
        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.price").value(129.99));
    }

    /** The 404 ProblemDetail that ApiExceptionHandler builds from ProductNotFoundException. */
    @Test
    void findByIdReturns404ForAnUnknownId() throws Exception {
        mockMvc.perform(get("/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.detail").value("No product with id 999999"));
    }

    /** Spring rejects the unbindable path variable before the service is ever called. */
    @Test
    void findByIdReturns400ForANonNumericId() throws Exception {
        mockMvc.perform(get("/products/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createdProductIsListedAfterwards() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Trackball\", \"price\": 59.95}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[10].name").value("Trackball"));
    }

    /**
     * Both fields are invalid, and both have to be reported: the handler collects every
     * field error rather than stopping at the first.
     */
    @Test
    void createRejectsAnInvalidPayloadWithFieldErrors() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"   \", \"price\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors").value(hasKey("name")))
                .andExpect(jsonPath("$.errors").value(hasKey("price")));
    }

    @Test
    void updateReplacesNameAndPrice() throws Exception {
        mockMvc.perform(put("/products/1")
                        .header(HttpHeaders.IF_MATCH, etagOf(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mechanical Keyboard MkII\", \"price\": 149.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard MkII"))
                .andExpect(jsonPath("$.price").value(149.99));

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard MkII"))
                .andExpect(jsonPath("$.price").value(149.99));
    }

    /**
     * createdAt belongs to the row, not the request. The update loads the entity before
     * saving it precisely so this value survives; building a fresh Product with the id set
     * would null it and hit the column's NOT NULL constraint.
     */
    @Test
    void updatePreservesCreatedAt() throws Exception {
        String before = mockMvc.perform(get("/products/1"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String createdAt = JsonPath.parse(before).read("$.createdAt", String.class);

        mockMvc.perform(put("/products/1")
                        .header(HttpHeaders.IF_MATCH, etagOf(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed\", \"price\": 1.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test
    void updateReturns404ForAnUnknownId() throws Exception {
        mockMvc.perform(put("/products/999999")
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Nothing\", \"price\": 1.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.detail").value("No product with id 999999"));
    }

    /** PUT replaces, so a missing price is a validation failure rather than "keep the old one". */
    @Test
    void updateRejectsAPartialPayload() throws Exception {
        mockMvc.perform(put("/products/1")
                        .header(HttpHeaders.IF_MATCH, etagOf(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Only a name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value(hasKey("price")));
    }

    /** No row may be added by an update, whatever the payload claims. */
    @Test
    void updateDoesNotChangeTheRowCount() throws Exception {
        mockMvc.perform(put("/products/2")
                        .header(HttpHeaders.IF_MATCH, etagOf(2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 999, \"name\": \"Renamed\", \"price\": 2.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));

        mockMvc.perform(get("/products"))
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void deleteRemovesTheProduct() throws Exception {
        mockMvc.perform(delete("/products/4"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/products/4"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/products"))
                .andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    void deleteReturns404ForAnUnknownId() throws Exception {
        mockMvc.perform(delete("/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.detail").value("No product with id 999999"));
    }

    /**
     * Pins the deliberate asymmetry documented on the endpoint: the row is gone either way,
     * but the status code is not idempotent on replay.
     */
    @Test
    void deleteTwiceReturns204ThenNotFound() throws Exception {
        mockMvc.perform(delete("/products/6"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/products/6"))
                .andExpect(status().isNotFound());
    }

    /**
     * id and createdAt are not components of ProductRequest, so Jackson has nowhere to put
     * them; the server assigns both regardless of what the client sends.
     *
     * <p>The assertions say "not what the client asked for" rather than naming an expected
     * id: H2 allocates identity values outside the transaction, so the rollback between
     * tests returns the rows but not the counter, and any exact id here would depend on how
     * many inserts ran before it.
     */
    @Test
    void clientCannotSetIdOrCreatedAt() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 999, "name": "Trackball", "price": 59.95,
                                 "createdAt": "2000-01-01T00:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.id").value(not(999)))
                .andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")));
    }

    /**
     * The lost-update scenario, which is the reason any of this exists. Two clients read the
     * same product, both write; the second write is built on a read that is now stale.
     * Without If-Match both would answer 200 and the first edit would vanish silently.
     */
    @Test
    void secondWriterWithAStaleEtagIsRejected() throws Exception {
        String etag = etagOf(5);

        mockMvc.perform(put("/products/5")
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Headset\", \"price\": 249.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/products/5")
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Headset Pro\", \"price\": 279.00}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.title").value("Precondition failed"));

        // The first writer's price survived; the stale write changed nothing.
        mockMvc.perform(get("/products/5"))
                .andExpect(jsonPath("$.name").value("Headset"))
                .andExpect(jsonPath("$.price").value(249.00));
    }

    @Test
    void updateWithoutIfMatchIsRefused() throws Exception {
        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Unconditional\", \"price\": 1.00}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.title").value("If-Match required"));

        mockMvc.perform(get("/products/1"))
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"));
    }

    @Test
    void getCarriesAnEtagAndUpdateReturnsTheNextOne() throws Exception {
        String before = etagOf(1);

        String after = mockMvc.perform(put("/products/1")
                        .header(HttpHeaders.IF_MATCH, before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bumped\", \"price\": 1.00}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        assertThat(after).isNotEqualTo(before);

        // The ETag the write returned is immediately usable: no re-read needed to edit again.
        mockMvc.perform(put("/products/1")
                        .header(HttpHeaders.IF_MATCH, after)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bumped twice\", \"price\": 2.00}"))
                .andExpect(status().isOk());
    }

    @Test
    void createCarriesAnEtagUsableWithoutAFollowUpRead() throws Exception {
        String etag = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Trackball\", \"price\": 59.95}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        String location = "/products/" + JsonPath.parse(
                        mockMvc.perform(get("/products")).andReturn().getResponse().getContentAsString())
                .read("$[-1].id", Integer.class);

        mockMvc.perform(put(location)
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Trackball Pro\", \"price\": 69.95}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteHonoursAStaleIfMatch() throws Exception {
        String stale = etagOf(7);

        mockMvc.perform(put("/products/7")
                        .header(HttpHeaders.IF_MATCH, stale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Moved on\", \"price\": 9.99}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/products/7").header(HttpHeaders.IF_MATCH, stale))
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(get("/products/7")).andExpect(status().isOk());
    }

    /** Reads a product purely for its ETag, the way any client has to before it can write. */
    private String etagOf(long id) throws Exception {
        return mockMvc.perform(get("/products/" + id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
    }
}

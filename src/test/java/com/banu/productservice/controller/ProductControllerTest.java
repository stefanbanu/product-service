package com.banu.productservice.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Boot 4 package: the Boot 3 home was org.springframework.boot.test.autoconfigure.web.servlet.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives POST /products through the real stack - validation, the service, Spring Data JDBC,
 * the Liquibase-created table - rather than a mocked service, because the parts worth
 * covering here are exactly the ones a mock would hide: that {@code @Valid} is wired, that
 * the identity column and auditing fill in id and createdAt, and that the row is readable
 * afterwards.
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
}

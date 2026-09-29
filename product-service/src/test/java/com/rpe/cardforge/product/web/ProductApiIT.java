package com.rpe.cardforge.product.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "JWT_ISSUER_URI=http://issuer.test/realms/cardforge",
      "JWT_JWK_SET_URI=http://issuer.test/certs"
    })
class ProductApiIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @Autowired MockMvc mvc;

  private static MockHttpServletRequestBuilder withScopes(
      MockHttpServletRequestBuilder request, String... scopes) {
    SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
    for (int i = 0; i < scopes.length; i++) {
      authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
    }
    return request.with(jwt().authorities(authorities));
  }

  private static String randomBin() {
    return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
  }

  private String createProduct(String bin) throws Exception {
    String body =
        mvc.perform(
                withScopes(post("/api/v1/products"), "products:write")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Gold","description":"Gold card","bin":"%s"}"""
                            .formatted(bin)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", startsWith("/api/v1/products/")))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  @Test
  void createsAndReadsProduct() throws Exception {
    String bin = randomBin();
    String id = createProduct(bin);

    mvc.perform(withScopes(get("/api/v1/products/" + id), "products:read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bin").value(bin))
        .andExpect(jsonPath("$.name").value("Gold"));
  }

  @Test
  void duplicateBinIsConflict() throws Exception {
    String bin = randomBin();
    createProduct(bin);

    mvc.perform(
            withScopes(post("/api/v1/products"), "products:write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Other\",\"bin\":\"%s\"}".formatted(bin)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://cardforge.rpe.com.br/problems/bin-already-registered"));
  }

  @Test
  void invalidFieldsAreUnprocessableWithFieldList() throws Exception {
    mvc.perform(
            withScopes(post("/api/v1/products"), "products:write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"bin\":\"12ab\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().string("Content-Type", "application/problem+json"))
        .andExpect(jsonPath("$.invalidFields[*].field", hasItem("bin")))
        .andExpect(jsonPath("$.invalidFields[*].field", hasItem("name")))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void malformedJsonIsBadRequest() throws Exception {
    mvc.perform(
            withScopes(post("/api/v1/products"), "products:write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/malformed-request"));
  }

  @Test
  void unknownProductIsNotFound() throws Exception {
    mvc.perform(withScopes(get("/api/v1/products/" + UUID.randomUUID()), "products:read"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/resource-not-found"));
  }

  @Test
  void cancelsProductAndRepeatedCancelIsIdempotent() throws Exception {
    String id = createProduct(randomBin());

    String first =
        mvc.perform(withScopes(post("/api/v1/products/" + id + "/cancel"), "products:write"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mvc.perform(withScopes(post("/api/v1/products/" + id + "/cancel"), "products:write"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.updatedAt").value((String) JsonPath.read(first, "$.updatedAt")));

    mvc.perform(withScopes(get("/api/v1/products/" + id), "products:read"))
        .andExpect(jsonPath("$.status").value("CANCELED"));
  }

  @Test
  void cancelRequiresWriteScopeAndExistingProduct() throws Exception {
    mvc.perform(
            withScopes(post("/api/v1/products/" + UUID.randomUUID() + "/cancel"), "products:read"))
        .andExpect(status().isForbidden());
    mvc.perform(
            withScopes(post("/api/v1/products/" + UUID.randomUUID() + "/cancel"), "products:write"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listsProductsWithPagination() throws Exception {
    createProduct(randomBin());
    String newest = createProduct(randomBin());

    mvc.perform(withScopes(get("/api/v1/products").param("size", "1"), "products:read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(newest))
        .andExpect(jsonPath("$.page.page").value(0))
        .andExpect(jsonPath("$.page.size").value(1))
        .andExpect(
            jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

    mvc.perform(withScopes(get("/api/v1/products"), "products:read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.size").value(20));
  }

  @Test
  void listingRejectsPageSizeAboveOneHundred() throws Exception {
    mvc.perform(withScopes(get("/api/v1/products").param("size", "101"), "products:read"))
        .andExpect(status().isBadRequest());
    mvc.perform(withScopes(get("/api/v1/products").param("page", "-1"), "products:read"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatesNameAndDescriptionKeepingBin() throws Exception {
    String bin = randomBin();
    String id = createProduct(bin);

    patch(id, "{\"name\":\"Platinum\",\"description\":\"Upgraded\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Platinum"))
        .andExpect(jsonPath("$.description").value("Upgraded"))
        .andExpect(jsonPath("$.bin").value(bin));

    patch(id, "{\"description\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Platinum"))
        .andExpect(jsonPath("$.description").doesNotExist());
  }

  @Test
  void binInUpdateIsRejectedAndNeverIgnored() throws Exception {
    String bin = randomBin();
    String id = createProduct(bin);

    patch(id, "{\"name\":\"Platinum\",\"bin\":\"%s\"}".formatted(randomBin()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/bin-immutable"));
    patch(id, "{\"bin\":null}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/bin-immutable"));

    mvc.perform(withScopes(get("/api/v1/products/" + id), "products:read"))
        .andExpect(jsonPath("$.name").value("Gold"))
        .andExpect(jsonPath("$.bin").value(bin));
  }

  @Test
  void canceledProductIsReadOnly() throws Exception {
    String id = createProduct(randomBin());
    mvc.perform(withScopes(post("/api/v1/products/" + id + "/cancel"), "products:write"))
        .andExpect(status().isOk());

    patch(id, "{\"name\":\"Too late\"}")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://cardforge.rpe.com.br/problems/product-canceled-read-only"));
  }

  @Test
  void invalidUpdateValuesAreUnprocessableAndWrongTypesAreBadRequest() throws Exception {
    String id = createProduct(randomBin());

    patch(id, "{\"name\":\" \",\"description\":\"%s\"}".formatted("x".repeat(501)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.invalidFields.length()").value(2));
    patch(id, "{\"name\":123}").andExpect(status().isBadRequest());
    patch(UUID.randomUUID().toString(), "{\"name\":\"Any\"}").andExpect(status().isNotFound());
  }

  private org.springframework.test.web.servlet.ResultActions patch(String id, String body)
      throws Exception {
    return mvc.perform(
        withScopes(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/products/" + id),
                "products:write")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void deleteIsNotAllowed() throws Exception {
    mvc.perform(withScopes(delete("/api/v1/products/" + UUID.randomUUID()), "products:write"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void requiresTokenAndScope() throws Exception {
    mvc.perform(get("/api/v1/products/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("WWW-Authenticate"))
        .andExpect(jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/unauthorized"));

    mvc.perform(
            withScopes(post("/api/v1/products"), "products:read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Gold\",\"bin\":\"%s\"}".formatted(randomBin())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/forbidden"));
  }

  @Test
  void echoesCorrelationId() throws Exception {
    mvc.perform(
            withScopes(get("/api/v1/products/" + UUID.randomUUID()), "products:read")
                .header("X-Correlation-Id", "it-corr-1"))
        .andExpect(header().string("X-Correlation-Id", "it-corr-1"))
        .andExpect(jsonPath("$.correlationId").value("it-corr-1"));
  }
}

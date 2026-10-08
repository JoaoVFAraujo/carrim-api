package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.carrim.application.identity.*;
import br.com.carrim.support.PostgresTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AnonymousIdentityTests extends PostgresTestSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityService service;

    @Autowired
    JdbcTemplate jdbc;

    static String proof() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String body(UUID id, String secret) {
        return "{\"installationId\":\"" + id + "\",\"installationSecret\":\"" + secret
                + "\",\"devicePlatform\":\"WEB\",\"appVersion\":\"0.0.1\"}";
    }

    private String bootstrap(UUID id, String secret) throws Exception {
        return mvc.perform(post("/api/v1/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, secret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("ANONYMOUS"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void issuedBearerAuthenticatesButCookiesDoNot() throws Exception {
        String response = bootstrap(UUID.randomUUID(), proof());
        String token = JsonPath.read(response, "$.accessToken");
        String owner = JsonPath.read(response, "$.userId");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(owner));
        mvc.perform(get("/api/v1/auth/me").cookie(new jakarta.servlet.http.Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicBootstrapCreatesOwnerWithoutTrustingClientUserId() throws Exception {
        UUID id = UUID.randomUUID();
        UUID spoof = UUID.randomUUID();
        String response = mvc.perform(post("/api/v1/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, proof()).replace("}", ",\"userId\":\"" + spoof + "\"}")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String owner = JsonPath.read(response, "$.userId");
        String token = JsonPath.read(response, "$.accessToken");
        assertNotEquals(spoof.toString(), owner);
        assertTrue(service.authenticate(token).isPresent());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM carrim.anonymous_installations WHERE id=?", Integer.class, id));
    }

    @Test
    void sameProofRecoversOwnerAndRotatesTokenWithoutCreatingAnotherUser() throws Exception {
        UUID id = UUID.randomUUID();
        String proof = proof();
        String first = bootstrap(id, proof);
        String second = bootstrap(id, proof);
        assertEquals((String) JsonPath.read(first, "$.userId"), (String) JsonPath.read(second, "$.userId"));
        String old = JsonPath.read(first, "$.accessToken");
        String next = JsonPath.read(second, "$.accessToken");
        assertNotEquals(old, next);
        assertTrue(service.authenticate(old).isEmpty());
        assertTrue(service.authenticate(next).isPresent());
    }

    @Test
    void installationUuidAloneDoesNotAllowAccountTakeover() throws Exception {
        UUID id = UUID.randomUUID();
        String original = bootstrap(id, proof());
        mvc.perform(post("/api/v1/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(id, proof())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_INSTALLATION_PROOF"));
        assertTrue(
                service.authenticate(JsonPath.read(original, "$.accessToken")).isPresent());
    }

    @Test
    void rejectsExpiredAndMalformedTokensAndAllowsProofBasedRecovery() throws Exception {
        UUID id = UUID.randomUUID();
        String proof = proof();
        String first = bootstrap(id, proof);
        String token = JsonPath.read(first, "$.accessToken");
        jdbc.update(
                "UPDATE carrim.anonymous_installations SET issued_at=?,expires_at=? WHERE id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(120)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                id);
        assertTrue(service.authenticate(token).isEmpty());
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        String next = JsonPath.read(bootstrap(id, proof), "$.accessToken");
        assertTrue(service.authenticate(next).isPresent());
    }

    @Test
    void databaseStoresOnlyHashesAndSecretsAreRedactedInObjectText() throws Exception {
        UUID id = UUID.randomUUID();
        String proof = proof();
        var issued = service.bootstrap(new BootstrapCommand(id, proof, "WEB", "0.0.1"));
        byte[] hash = jdbc.queryForObject(
                "SELECT access_token_hash FROM carrim.anonymous_installations WHERE id=?", byte[].class, id);
        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(issued.accessToken().getBytes(StandardCharsets.US_ASCII)),
                hash);
        assertFalse(issued.toString().contains(issued.accessToken()));
        assertFalse(new BootstrapCommand(id, proof, "WEB", "0.0.1").toString().contains(proof));
    }

    @Test
    void concurrentBootstrapPreservesOneOwnerAndInstallation() throws Exception {
        UUID id = UUID.randomUUID();
        String proof = proof();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(
                    () -> service.bootstrap(new BootstrapCommand(id, proof, "WEB", "0.0.1")), executor);
            var second = CompletableFuture.supplyAsync(
                    () -> service.bootstrap(new BootstrapCommand(id, proof, "WEB", "0.0.1")), executor);
            assertEquals(first.get().userId(), second.get().userId());
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM carrim.anonymous_installations WHERE id=?", Integer.class, id));
    }

    @Test
    void invalidBootstrapDoesNotExposeProofOrInternalDetails() throws Exception {
        String invalid = "sensitive-invalid-secret";
        String response = mvc.perform(post("/api/v1/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), invalid)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(response.contains(invalid));
        assertFalse(response.contains("Exception"));
        mvc.perform(post("/api/v1/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), proof()).replace("WEB", "UNKNOWN")))
                .andExpect(status().isBadRequest());
    }
}

package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.carrim.application.identity.BootstrapCommand;
import br.com.carrim.application.identity.IdentityService;
import br.com.carrim.support.PostgresTestSupport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SyncApiTests extends PostgresTestSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityService identities;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    private String actor() {
        return identities
                .bootstrap(new BootstrapCommand(UUID.randomUUID(), AnonymousIdentityTests.proof(), "WEB", "0.0.2"))
                .accessToken();
    }

    private Map<String, Object> market(UUID operation, UUID id, String name) {
        return Map.of(
                "operationId",
                operation,
                "aggregateType",
                "SUPERMARKET",
                "aggregateId",
                id,
                "operation",
                "CREATE",
                "payload",
                Map.of("name", name));
    }

    private String push(String token, Object... operations) throws Exception {
        return mvc.perform(post("/api/v1/sync/push")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("operations", List.of(operations)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void replayIsAtomicAndPayloadReuseIsRejected() throws Exception {
        String token = actor();
        UUID id = UUID.randomUUID(), op = UUID.randomUUID();
        assertTrue(push(token, market(op, id, "Market")).contains("APPLIED"));
        assertTrue(push(token, market(op, id, "Market")).contains("ALREADY_APPLIED"));
        assertTrue(push(token, market(op, id, "Changed")).contains("IDEMPOTENCY_CONFLICT"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM carrim.supermarkets WHERE id=?", Integer.class, id));
        assertEquals(
                "Market", jdbc.queryForObject("SELECT name FROM carrim.supermarkets WHERE id=?", String.class, id));
    }

    @Test
    void concurrentReplayCreatesOneReceipt() throws Exception {
        String token = actor();
        UUID id = UUID.randomUUID(), op = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> push(token, market(op, id, "Concurrent")));
            var b = executor.submit(() -> push(token, market(op, id, "Concurrent")));
            var results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(
                    1,
                    results.stream().filter(r -> r.contains("ALREADY_APPLIED")).count());
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM carrim.sync_receipts WHERE operation_id=?", Integer.class, op));
    }

    @Test
    void rejectsInvalidPayloadWithoutReceiptAndStopsDependents() throws Exception {
        String token = actor();
        UUID op = UUID.randomUUID(), later = UUID.randomUUID();
        String result = push(token, market(op, UUID.randomUUID(), " "), market(UUID.randomUUID(), later, "Later"));
        assertTrue(result.contains("REJECTED"));
        assertTrue(result.contains("NOT_PROCESSED"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM carrim.sync_receipts WHERE operation_id=?", Integer.class, op));
        assertEquals(
                0, jdbc.queryForObject("SELECT count(*) FROM carrim.supermarkets WHERE id=?", Integer.class, later));
    }

    @Test
    void cannotUpdateAnotherOwnersMarketAndReportsOwnConflictState() throws Exception {
        String token = actor();
        UUID id = UUID.randomUUID();
        push(token, market(UUID.randomUUID(), id, "Owned"));
        var update = Map.of(
                "operationId",
                UUID.randomUUID(),
                "aggregateType",
                "SUPERMARKET",
                "aggregateId",
                id,
                "operation",
                "UPDATE",
                "baseVersion",
                999,
                "payload",
                Map.of("name", "Changed"));
        assertTrue(push(token, update).contains("VERSION_CONFLICT"));
        String other = push(actor(), update);
        assertTrue(other.contains("REJECTED"));
        assertFalse(other.contains("Owned"));
    }

    @Test
    void anonymousAndOversizedBatchesAreDenied() throws Exception {
        mvc.perform(post("/api/v1/sync/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operations\":[]}"))
                .andExpect(status().isUnauthorized());
        var operations = Collections.nCopies(51, market(UUID.randomUUID(), UUID.randomUUID(), "Limit"));
        mvc.perform(post("/api/v1/sync/push")
                        .header("Authorization", "Bearer " + actor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("operations", operations))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pullIsOrderedPaginatedAndOwnerScoped() throws Exception {
        String token = actor();
        UUID id = UUID.randomUUID();
        push(token, market(UUID.randomUUID(), id, "Pull market"));
        String first = mvc.perform(
                        get("/api/v1/sync/pull").param("limit", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var page = mapper.readTree(first);
        assertEquals(
                id.toString(), page.path("changes").get(0).path("aggregateId").asString());
        String cursor = page.path("cursor").asString();
        mvc.perform(get("/api/v1/sync/pull").param("cursor", cursor).header("Authorization", "Bearer " + actor()))
                .andExpect(status().isBadRequest());
        String empty = mvc.perform(
                        get("/api/v1/sync/pull").param("cursor", cursor).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(0, mapper.readTree(empty).path("changes").size());
        // Direct REST writes are journaled too, not just the sync endpoint.
        mvc.perform(put("/api/v1/supermarkets/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Updated directly\"}"))
                .andExpect(status().isOk());
        String changed = mvc.perform(
                        get("/api/v1/sync/pull").param("cursor", cursor).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(changed.contains("Updated directly"));
    }

    @Test
    void capsApiRequestBodiesBeforeParsing() throws Exception {
        mvc.perform(post("/api/v1/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(" ".repeat(2 * 1024 * 1024 + 1)))
                .andExpect(status().isPayloadTooLarge());
    }
}

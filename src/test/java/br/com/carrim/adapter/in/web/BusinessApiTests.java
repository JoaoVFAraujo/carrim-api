package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.carrim.application.identity.BootstrapCommand;
import br.com.carrim.application.identity.IdentityService;
import br.com.carrim.application.shopping.CompletionRepository;
import br.com.carrim.application.shopping.ShoppingRepository;
import br.com.carrim.domain.shared.Money;
import br.com.carrim.support.PostgresTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class BusinessApiTests extends PostgresTestSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityService identities;

    @Autowired
    CompletionRepository completion;

    @Autowired
    ShoppingRepository shopping;

    @Autowired
    JdbcTemplate jdbc;

    private record Actor(UUID owner, String token) {}

    private Actor actor() {
        var issued = identities.bootstrap(
                new BootstrapCommand(UUID.randomUUID(), AnonymousIdentityTests.proof(), "WEB", "0.0.1"));
        return new Actor(issued.userId(), issued.accessToken());
    }

    @Test
    void bootstrapIsRateLimitedWithoutTrustingForwardedAddress() throws Exception {
        String body = AnonymousIdentityTests.body(UUID.randomUUID(), AnonymousIdentityTests.proof());
        for (int i = 0; i < 60; i++)
            mvc.perform(post("/api/v1/auth/anonymous")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.99");
                                return request;
                            }))
                    .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Forwarded-For", "198.51.100.100")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.99");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void completionNormalizesSubMicrosecondTimesForStoredReplay() throws Exception {
        Actor a = actor();
        UUID id = start(a, market(a));
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/items"),
                201,
                addBody(0, unit(UUID.randomUUID(), null, 1, 799)));
        UUID key = UUID.randomUUID();
        String body = closeBody(1, 799).replace("10:01:00Z", "10:01:00.0000007Z");
        String first = request(
                a, post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", key), 200, body);
        assertEquals("2026-10-07T10:01:00Z", (String) JsonPath.read(first, "$.finishedAt"));
        assertEquals(
                first,
                request(
                        a,
                        post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", key),
                        200,
                        body));
    }

    private String request(Actor actor, MockHttpServletRequestBuilder request, int status, String body)
            throws Exception {
        request.header("Authorization", "Bearer " + actor.token());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request)
                .andExpect(status().is(status))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UUID market(Actor actor) throws Exception {
        UUID id = UUID.randomUUID();
        request(actor, post("/api/v1/supermarkets"), 201, "{\"id\":\"" + id + "\",\"name\":\"Market\"}");
        return id;
    }

    private UUID start(Actor actor, UUID market) throws Exception {
        UUID id = UUID.randomUUID();
        request(
                actor,
                post("/api/v1/shopping-sessions"),
                201,
                "{\"id\":\"" + id + "\",\"supermarketId\":\"" + market + "\",\"startedAt\":\"2026-10-07T10:00:00Z\"}");
        return id;
    }

    private String unit(UUID id, UUID product, int qty, long price) {
        return "{\"id\":\"" + id + "\",\"productId\":" + (product == null ? "null" : "\"" + product + "\"")
                + ",\"productNameSnapshot\":\"Coffee\",\"measurementType\":\"UNIT\",\"pricingType\":\"REGULAR\",\"quantityUnits\":"
                + qty + ",\"unitPriceCents\":" + price + "}";
    }

    private String addBody(long version, String item) {
        return "{\"version\":" + version + ",\"item\":" + item + "}";
    }

    private String closeBody(long version, long checkout) {
        return "{\"version\":" + version + ",\"completedAt\":\"2026-10-07T10:01:00Z\",\"checkoutTotalCents\":"
                + checkout + "}";
    }

    @Test
    void receiptFailureRollsBackPurchaseAndPriceHistory() throws Exception {
        Actor a = actor();
        UUID id = start(a, market(a));
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/items"),
                201,
                addBody(0, unit(UUID.randomUUID(), null, 1, 799)));
        jdbc.execute(
                "CREATE FUNCTION carrim.test_receipt_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.session_id='"
                        + id
                        + "'::uuid THEN RAISE EXCEPTION 'injected receipt failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        jdbc.execute(
                "CREATE TRIGGER test_receipt_failure BEFORE INSERT ON carrim.completion_receipts FOR EACH ROW EXECUTE FUNCTION carrim.test_receipt_failure()");
        UUID key = UUID.randomUUID();
        try {
            request(
                    a,
                    post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", key),
                    409,
                    closeBody(1, 799));
            assertEquals(
                    "ACTIVE",
                    shopping.find(a.owner(), id).orElseThrow().value().status().name());
            assertEquals(1, shopping.find(a.owner(), id).orElseThrow().version());
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM carrim.price_observations WHERE session_id=?", Integer.class, id));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM carrim.completion_receipts WHERE user_id=? AND operation_id=?",
                            Integer.class,
                            a.owner(),
                            key));
        } finally {
            jdbc.execute("DROP TRIGGER test_receipt_failure ON carrim.completion_receipts");
            jdbc.execute("DROP FUNCTION carrim.test_receipt_failure()");
        }
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", key),
                200,
                closeBody(1, 799));
    }

    @Test
    void catalogIsPrivateVersionedAndBarcodeRemainsText() throws Exception {
        Actor a = actor();
        Actor b = actor();
        UUID id = UUID.randomUUID();
        String payload = "{\"id\":\"" + id
                + "\",\"name\":\"Coffee\",\"barcode\":\"0789600112233\",\"measurementType\":\"UNIT\",\"userId\":\""
                + b.owner() + "\"}";
        request(a, post("/api/v1/products"), 201, payload);
        request(b, get("/api/v1/products/" + id), 404, null);
        assertEquals(0, (int) JsonPath.read(request(b, get("/api/v1/products"), 200, null), "$.length()"));
        String own = request(a, get("/api/v1/products/by-barcode/0789600112233"), 200, null);
        assertEquals("0789600112233", (String) JsonPath.read(own, "$.product.barcode"));
        request(
                a,
                put("/api/v1/products/" + id),
                200,
                "{\"version\":0,\"name\":\"Renamed\",\"barcode\":\"0789600112233\",\"measurementType\":\"UNIT\"}");
        request(
                a,
                put("/api/v1/products/" + id),
                409,
                "{\"version\":0,\"name\":\"Stale\",\"measurementType\":\"UNIT\"}");
        request(
                b,
                put("/api/v1/products/" + id),
                404,
                "{\"version\":1,\"name\":\"Other\",\"measurementType\":\"UNIT\"}");
    }

    @Test
    void fullHttpBasketSupportsExactWeightBundleAndCompletionReplay() throws Exception {
        Actor a = actor();
        UUID market = market(a);
        request(a, get("/api/v1/shopping-sessions/active"), 204, null);
        UUID id = start(a, market);
        request(a, get("/api/v1/shopping-sessions/active"), 200, null);
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/items"),
                201,
                addBody(0, unit(UUID.randomUUID(), null, 2, 799)));
        String weight = "{\"id\":\"" + UUID.randomUUID()
                + "\",\"productNameSnapshot\":\"Banana\",\"measurementType\":\"WEIGHT\",\"pricingType\":\"REGULAR\",\"weightGrams\":824,\"pricePerKgCents\":699}";
        request(a, post("/api/v1/shopping-sessions/" + id + "/items"), 201, addBody(1, weight));
        String bundle = "{\"id\":\"" + UUID.randomUUID()
                + "\",\"productNameSnapshot\":\"Milk\",\"measurementType\":\"UNIT\",\"pricingType\":\"BUNDLE\",\"quantityUnits\":6,\"bundleQuantity\":3,\"bundlePriceCents\":1000}";
        request(a, post("/api/v1/shopping-sessions/" + id + "/items"), 201, addBody(2, bundle));
        UUID key = UUID.randomUUID();
        String body = closeBody(3, 4200);
        String first = request(
                a, post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", key), 200, body);
        assertEquals(4174, (int) JsonPath.read(first, "$.calculatedTotalCents"));
        assertEquals(26, (int) JsonPath.read(first, "$.checkoutDifferenceCents"));
        assertEquals(
                first,
                request(
                        a,
                        post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", key),
                        200,
                        body));
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", key),
                409,
                closeBody(3, 4300));
        String history = request(a, get("/api/v1/shopping-sessions/" + id + "/prices"), 200, null);
        assertEquals(3, (int) JsonPath.read(history, "$.length()"));
        assertEquals(333, (int) JsonPath.read(history, "$[2].normalizedPriceCents"));
        request(a, get("/api/v1/shopping-sessions/active"), 204, null);
        assertEquals(1, (int)
                JsonPath.read(request(a, get("/api/v1/shopping-sessions?status=COMPLETED"), 200, null), "$.length()"));
    }

    @Test
    void foreignResourcesCannotBeReadOrMutated() throws Exception {
        Actor a = actor();
        Actor b = actor();
        UUID market = market(a);
        UUID id = start(a, market);
        request(b, get("/api/v1/supermarkets/" + market), 404, null);
        request(b, get("/api/v1/shopping-sessions/" + id), 404, null);
        request(b, get("/api/v1/shopping-sessions/" + id + "/prices"), 404, null);
        request(b, patch("/api/v1/shopping-sessions/" + id), 404, "{\"version\":0,\"budgetCents\":1000}");
        request(
                b,
                post("/api/v1/shopping-sessions/" + id + "/items"),
                404,
                addBody(0, unit(UUID.randomUUID(), null, 1, 1)));
        request(
                b,
                post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", UUID.randomUUID()),
                404,
                closeBody(0, 0));
        request(
                b,
                post("/api/v1/shopping-sessions"),
                404,
                "{\"id\":\"" + UUID.randomUUID() + "\",\"supermarketId\":\"" + market
                        + "\",\"startedAt\":\"2026-10-07T10:00:00Z\"}");
    }

    @Test
    void itemEditRemovalBudgetAndCancelUseSessionVersion() throws Exception {
        Actor a = actor();
        UUID id = start(a, market(a));
        UUID item = UUID.randomUUID();
        request(a, post("/api/v1/shopping-sessions/" + id + "/items"), 201, addBody(0, unit(item, null, 1, 799)));
        request(
                a,
                put("/api/v1/shopping-sessions/" + id + "/items/" + item),
                200,
                addBody(1, unit(item, null, 2, 799)));
        request(a, patch("/api/v1/shopping-sessions/" + id), 200, "{\"version\":2,\"budgetCents\":2000}");
        request(a, delete("/api/v1/shopping-sessions/" + id + "/items/" + item + "?version=3"), 200, null);
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/cancel"),
                200,
                "{\"version\":4,\"canceledAt\":\"2026-10-07T10:01:00Z\"}");
        request(a, patch("/api/v1/shopping-sessions/" + id), 409, "{\"version\":5,\"budgetCents\":1000}");
        assertEquals(0, (int)
                JsonPath.read(request(a, get("/api/v1/shopping-sessions/" + id + "/prices"), 200, null), "$.length()"));
    }

    @Test
    void lastPriceIsMarketScopedAndDoesNotUseBundleEquivalent() throws Exception {
        Actor a = actor();
        UUID market = market(a);
        UUID product = UUID.randomUUID();
        request(
                a,
                post("/api/v1/products"),
                201,
                "{\"id\":\"" + product
                        + "\",\"name\":\"Coffee\",\"barcode\":\"12345678\",\"measurementType\":\"UNIT\"}");
        UUID id = start(a, market);
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/items"),
                201,
                addBody(0, unit(UUID.randomUUID(), product, 1, 799)));
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", UUID.randomUUID()),
                200,
                closeBody(1, 799));
        UUID next = start(a, market);
        String bundle = "{\"id\":\"" + UUID.randomUUID() + "\",\"productId\":\"" + product
                + "\",\"productNameSnapshot\":\"Promo\",\"measurementType\":\"UNIT\",\"pricingType\":\"BUNDLE\",\"quantityUnits\":3,\"bundleQuantity\":3,\"bundlePriceCents\":1000}";
        request(a, post("/api/v1/shopping-sessions/" + next + "/items"), 201, addBody(0, bundle));
        request(
                a,
                post("/api/v1/shopping-sessions/" + next + "/complete").header("Idempotency-Key", UUID.randomUUID()),
                200,
                closeBody(1, 1000));
        String lookup = request(a, get("/api/v1/products/by-barcode/12345678?supermarketId=" + market), 200, null);
        assertEquals(799, (int) JsonPath.read(lookup, "$.lastPrice.priceCents"));
        String other = request(a, get("/api/v1/products/by-barcode/12345678?supermarketId=" + market(a)), 200, null);
        assertNull(JsonPath.read(other, "$.lastPrice"));
    }

    @Test
    void validationRejectsFractionalCentsUnknownIdsAndInvalidPages() throws Exception {
        Actor a = actor();
        UUID id = start(a, market(a));
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/items"),
                400,
                addBody(0, unit(UUID.randomUUID(), null, 1, 799)).replace("799", "799.5"));
        request(a, get("/api/v1/products?limit=101"), 400, null);
        request(a, get("/api/v1/products/by-barcode/invalid"), 400, null);
        request(a, get("/api/v1/shopping-sessions?status=INVALID"), 400, null);
        request(a, get("/api/v1/supermarkets/not-a-uuid"), 400, null);
        request(a, post("/api/v1/shopping-sessions/" + id + "/complete"), 400, closeBody(0, 0));
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/complete").header("Idempotency-Key", UUID.randomUUID()),
                400,
                closeBody(0, -1));
        assertTrue(shopping.find(a.owner(), id).orElseThrow().value().items().isEmpty());
    }

    @Test
    void concurrentSameKeyFinalizationReplaysOneResult() throws Exception {
        Actor a = actor();
        UUID id = start(a, market(a));
        request(
                a,
                post("/api/v1/shopping-sessions/" + id + "/items"),
                201,
                addBody(0, unit(UUID.randomUUID(), null, 1, 799)));
        UUID key = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = CompletableFuture.supplyAsync(
                    () -> completion.complete(
                            a.owner(), id, 1, Instant.parse("2026-10-07T10:01:00Z"), new Money(799), key),
                    executor);
            var two = CompletableFuture.supplyAsync(
                    () -> completion.complete(
                            a.owner(), id, 1, Instant.parse("2026-10-07T10:01:00Z"), new Money(799), key),
                    executor);
            assertEquals(one.get(), two.get());
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM carrim.price_observations WHERE session_id=?", Integer.class, id));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM carrim.completion_receipts WHERE user_id=? AND operation_id=?",
                        Integer.class,
                        a.owner(),
                        key));
    }

    @Test
    void missingOrExpiredBearerCannotReachBusinessRoutes() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/supermarkets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        Actor a = actor();
        jdbc.update(
                "UPDATE carrim.anonymous_installations SET issued_at=now()-interval '2 minutes',expires_at=now()-interval '1 minute' WHERE user_id=?",
                a.owner());
        request(a, get("/api/v1/products"), 401, null);
    }
}

package br.com.carrim.adapter.out.catalog;

import static org.junit.jupiter.api.Assertions.*;

import br.com.carrim.application.catalog.ProductLookup;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OpenFoodFactsClientTests {
    static final String CODE = "7891000100103";
    static final String JSON = """
        {"status":"success","code":"7891000100103","product":{
          "product_name":"English","product_name_pt":"Café\\nBom","brands":"Brand","quantity":"500 g",
          "image_front_small_url":"https://images.openfoodfacts.org/images/products/789/100/010/0103/front_pt.1.200.jpg"}}
        """;

    @FunctionalInterface
    interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
    }

    @FunctionalInterface
    interface Check {
        void check(OpenFoodFactsClient client) throws Exception;
    }

    void server(Handler handler, Duration timeout, Check check) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try {
                    handler.handle(exchange);
                } catch (Exception ignored) {
                    /* Closing a cancelled response is expected in timeout tests. */
                } finally {
                    exchange.close();
                }
            });
            server.start();
            var uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/");
            var client = new OpenFoodFactsClient(
                    JsonMapper.builder().build(),
                    HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build(),
                    Clock.systemUTC(),
                    uri,
                    timeout);
            check.check(client);
        } finally {
            server.stop(0);
        }
    }

    void reply(com.sun.net.httpserver.HttpExchange e, int status, String body) throws Exception {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
    }

    @Test
    void mapsVerifiedV3ContractWithoutSendingPrivateCredentials() throws Exception {
        server(
                e -> {
                    assertNull(e.getRequestHeaders().getFirst("Authorization"));
                    assertNull(e.getRequestHeaders().getFirst("Cookie"));
                    assertTrue(e.getRequestHeaders().getFirst("User-Agent").startsWith("Carrim/"));
                    assertEquals("/" + CODE + ".json", e.getRequestURI().getPath());
                    reply(e, 200, JSON);
                },
                Duration.ofSeconds(2),
                client -> {
                    var result = client.lookup(CODE).product();
                    assertEquals(ProductLookup.Status.FOUND, result.status());
                    assertEquals("Café Bom", result.name());
                    assertEquals("500 g", result.quantity());
                    assertNotNull(result.imageUrl());
                    assertNull(result.measurementType());
                });
    }

    @Test
    void supportsPaddedCodesButRejectsDifferentProductAndMalformedPayload() throws Exception {
        server(
                e -> reply(e, 200, JSON.replace("7891000100103", "00012345678905")),
                Duration.ofSeconds(2),
                client -> assertEquals(
                        ProductLookup.Status.FOUND,
                        client.lookup("012345678905").product().status()));
        for (String bad : new String[] {"[]", "{}", "not json", JSON.replace(CODE, "1111111111111")}) {
            server(
                    e -> reply(e, 200, bad),
                    Duration.ofSeconds(2),
                    client -> assertEquals(
                            ProductLookup.Status.UNAVAILABLE,
                            client.lookup(CODE).product().status()));
        }
    }

    @Test
    void missingNamesReturnManualFallbackAndImagesCannotEscapeAllowlist() throws Exception {
        server(
                e -> reply(e, 200, JSON.replace("\"English\"", "null").replace("\"Café\\nBom\"", "{}")),
                Duration.ofSeconds(2),
                client -> assertEquals(
                        ProductLookup.Status.NOT_FOUND,
                        client.lookup(CODE).product().status()));
        for (String bad : new String[] {
            "http://images.openfoodfacts.org/a.jpg",
            "https://localhost/a.jpg",
            "https://images.openfoodfacts.org.evil.test/a.jpg",
            "https://images.openfoodfacts.org/images/products/a.jpg?token=x"
        }) {
            server(
                    e -> reply(
                            e,
                            200,
                            JSON.replace(
                                    "https://images.openfoodfacts.org/images/products/789/100/010/0103/front_pt.1.200.jpg",
                                    bad)),
                    Duration.ofSeconds(2),
                    client -> assertNull(client.lookup(CODE).product().imageUrl()));
        }
    }

    @Test
    void handlesAbsentRateLimitAndRedirectWithoutFollowing() throws Exception {
        server(
                e -> reply(e, 404, "{}"),
                Duration.ofSeconds(2),
                client -> assertEquals(
                        ProductLookup.Status.NOT_FOUND,
                        client.lookup(CODE).product().status()));
        server(
                e -> {
                    e.getResponseHeaders().set("Retry-After", "120");
                    reply(e, 429, "{}");
                },
                Duration.ofSeconds(2),
                client -> {
                    var reply = client.lookup(CODE);
                    assertEquals(
                            ProductLookup.Status.RATE_LIMITED, reply.product().status());
                    assertEquals(120, reply.retryAfterSeconds());
                });
        var calls = new AtomicInteger();
        server(
                e -> {
                    calls.incrementAndGet();
                    e.getResponseHeaders().set("Location", "/secret");
                    reply(e, 302, "{}");
                },
                Duration.ofSeconds(2),
                client -> {
                    assertEquals(
                            ProductLookup.Status.UNAVAILABLE,
                            client.lookup(CODE).product().status());
                    assertEquals(1, calls.get());
                });
    }

    @Test
    void boundsResponseSizeAndTimeoutIncludingStalledBody() throws Exception {
        server(
                e -> reply(e, 200, "x".repeat(65537)),
                Duration.ofSeconds(2),
                client -> assertEquals(
                        ProductLookup.Status.UNAVAILABLE,
                        client.lookup(CODE).product().status()));
        server(
                e -> {
                    e.sendResponseHeaders(200, 0);
                    e.getResponseBody().write('{');
                    e.getResponseBody().flush();
                    Thread.sleep(600);
                },
                Duration.ofMillis(100),
                client -> {
                    long start = System.nanoTime();
                    assertEquals(
                            ProductLookup.Status.UNAVAILABLE,
                            client.lookup(CODE).product().status());
                    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1000);
                });
    }
}

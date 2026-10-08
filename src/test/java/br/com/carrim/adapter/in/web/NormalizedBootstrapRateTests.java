package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.carrim.support.PostgresTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "test.bootstrap.normalized-path=true"})
class NormalizedBootstrapRateTests extends PostgresTestSupport {
    @Autowired
    Environment environment;

    @Test
    @Timeout(45)
    void encodedAndCanonicalBootstrapRequestsShareTheSameRateLimit() throws Exception {
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + "/api/v1/auth/";
        String body = AnonymousIdentityTests.body(UUID.randomUUID(), AnonymousIdentityTests.proof());
        try (var client = HttpClient.newHttpClient()) {
            for (int index = 0; index < 60; index++) {
                String suffix = index % 2 == 0 ? "%61nonymous" : "anonymous";
                var response = client.send(request(base + suffix, body), HttpResponse.BodyHandlers.discarding());
                assertEquals(200, response.statusCode());
            }
            for (String suffix : new String[] {"%61nonymous", "anonymous"}) {
                var limited = client.send(request(base + suffix, body), HttpResponse.BodyHandlers.discarding());
                assertEquals(429, limited.statusCode());
                assertEquals("60", limited.headers().firstValue("Retry-After").orElseThrow());
            }
        }
    }

    private static HttpRequest request(String url, String body) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}

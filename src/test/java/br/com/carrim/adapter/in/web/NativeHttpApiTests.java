package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.carrim.support.PostgresTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
class NativeHttpApiTests extends PostgresTestSupport {
    @Autowired
    Environment environment;

    @Test
    void serverAuthenticatesRealHttpRequestsAgainstPostgres() throws Exception {
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + "/api/v1";
        try (var client = HttpClient.newHttpClient()) {
            var issued = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/auth/anonymous"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    AnonymousIdentityTests.body(UUID.randomUUID(), AnonymousIdentityTests.proof())))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, issued.statusCode());
            String token = JsonPath.read(issued.body(), "$.accessToken");
            var catalog = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/products"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, catalog.statusCode());
            assertEquals("[]", catalog.body());
            var denied = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/products"))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, denied.statusCode());
        }
    }
}

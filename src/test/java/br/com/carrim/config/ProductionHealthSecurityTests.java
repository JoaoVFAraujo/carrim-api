package br.com.carrim.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@WebMvcTest(
        properties = {
            "spring.datasource.url=jdbc:postgresql://localhost/test?sslmode=verify-full",
            "management.server.port=9090"
        })
@ContextConfiguration(classes = {ProductionConfiguration.class, ProductionHealthSecurityTests.Health.class})
@ActiveProfiles("prod")
class ProductionHealthSecurityTests {
    @RestController
    static class Health {
        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void rejectedPublicHealthDoesNotAllocateSessionOrSaveRequest() throws Exception {
        var result = mvc.perform(get("/actuator/health").with(request -> {
                    request.setLocalPort(8082);
                    return request;
                }))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void healthIsAvailableOnlyOnLoopbackManagementPort() throws Exception {
        mvc.perform(get("/actuator/health").with(request -> {
                    request.setLocalPort(9090);
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health").with(request -> {
                    request.setLocalPort(9090);
                    request.setRemoteAddr("198.51.100.1");
                    return request;
                }))
                .andExpect(status().isUnauthorized());
    }
}

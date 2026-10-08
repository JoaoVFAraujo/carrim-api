package br.com.carrim.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class BootstrapRouteTests {
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/anonymous", "/api/v1/auth/%61nonymous", "/api/v1/auth/anonymous;x=1"})
    void equivalentBootstrapPathsAreIncluded(String path) {
        var request = new MockHttpServletRequest("POST", path);
        assertFalse(new BootstrapRateFilter().shouldNotFilter(request));
        var contextual = new MockHttpServletRequest("POST", "/carrim" + path);
        contextual.setContextPath("/carrim");
        assertFalse(new BootstrapRateFilter().shouldNotFilter(contextual));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT"})
    void onlyBootstrapPostRequestsAreCounted(String method) {
        assertTrue(new BootstrapRateFilter()
                .shouldNotFilter(new MockHttpServletRequest(method, "/api/v1/auth/anonymous")));
    }
}

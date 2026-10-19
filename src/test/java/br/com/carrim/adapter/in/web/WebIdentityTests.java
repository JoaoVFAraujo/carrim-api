package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.carrim.support.PostgresTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class WebIdentityTests extends PostgresTestSupport {
    @Autowired
    MockMvc mvc;

    private MockHttpServletRequestBuilder web(String route, String body) {
        return post("/api/v1/auth/web/" + route)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Carrim-Web", "1")
                .header("Sec-Fetch-Site", "same-origin")
                .content(body);
    }

    @Test
    void productionRefusesCleartextEvenOnLoopback() {
        var environment = new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles("prod");
        var controller = new WebIdentityController(null, environment);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServerName("localhost");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Carrim-Web", "1");
        var error = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> controller.prepare(
                        new WebIdentityController.ExpectedInstallation(null),
                        request,
                        new org.springframework.mock.web.MockHttpServletResponse()));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void protectsProofAndRecoversSameOwnerAfterReloadWithoutCookieBusinessAuthentication() throws Exception {
        var prepared = mvc.perform(web("prepare", "{}").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse();
        String header = prepared.getHeader("Set-Cookie");
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=Strict"));
        assertFalse(header.contains("Domain="));
        String id = JsonPath.read(prepared.getContentAsString(), "$.installationId");
        Cookie cookie = prepared.getCookie("__Host-carrim_web_identity");
        assertNotNull(cookie);
        assertFalse(prepared.getContentAsString().contains(cookie.getValue().substring(37)));
        String body = "{\"installationId\":\"" + id + "\"}";
        String first = mvc.perform(web("token", body).secure(true).cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(web("prepare", body).secure(true).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationId").value(id));
        String second = mvc.perform(web("token", body).secure(true).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value((String) JsonPath.read(first, "$.userId")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(get("/api/v1/products").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + JsonPath.read(second, "$.accessToken")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + JsonPath.read(first, "$.accessToken")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void neverReplacesMissingDamagedOrDifferentProofOnBoundDatabase() throws Exception {
        String expected = "{\"installationId\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(web("prepare", expected))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(web("token", expected)).andExpect(status().isUnauthorized());
        mvc.perform(web("prepare", "{}").cookie(new Cookie("carrim_web_identity", "invalid")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
        var response = mvc.perform(web("prepare", "{}")).andReturn().getResponse();
        Cookie cookie = response.getCookie("carrim_web_identity");
        mvc.perform(web("prepare", expected).cookie(cookie)).andExpect(status().isConflict());
        mvc.perform(web("token", expected).cookie(cookie)).andExpect(status().isConflict());
    }

    @Test
    void rejectsCsrfForeignSitesAndNonLocalCleartextWithoutOpeningCors() throws Exception {
        mvc.perform(post("/api/v1/auth/web/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        for (String site : new String[] {"cross-site", "same-site", "none"})
            mvc.perform(web("prepare", "{}").header("Sec-Fetch-Site", site)).andExpect(status().isForbidden());
        mvc.perform(web("prepare", "{}").with(request -> {
                    request.setServerName("api.example.com");
                    return request;
                }))
                .andExpect(status().isForbidden());
        mvc.perform(web("prepare", "{}").header("Origin", "https://foreign.example"))
                .andExpect(status().isForbidden());
        mvc.perform(web("prepare", "{}").with(request -> {
                    request.setRemoteAddr("203.0.113.1");
                    return request;
                }))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/web/token")
                        .header("Origin", "https://foreign.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-carrim-web"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

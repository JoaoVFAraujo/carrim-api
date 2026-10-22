package br.com.carrim.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

class RenderConfigurationTests {
    private boolean preflight(String origin, String path, String headers) throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", path);
        request.setScheme("https");
        request.setServerName("api.onrender.com");
        request.setServerPort(443);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", headers);
        var policy = new RenderConfiguration().nativeCorsConfigurationSource().getCorsConfiguration(request);
        var response = new MockHttpServletResponse();
        boolean allowed = new DefaultCorsProcessor().processRequest(policy, request, response);
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
        return allowed && origin.equals(response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void nativeBearerPreflightIsNarrowAndDoesNotAllowWebCookiesOrAdmin() throws Exception {
        assertTrue(preflight("https://localhost", "/api/v1/auth/anonymous", "content-type"));
        assertTrue(preflight("https://localhost", "/api/v1/sync/push", "authorization,content-type"));
        assertFalse(preflight("https://evil.example", "/api/v1/sync/push", "authorization"));
        assertFalse(preflight("http://localhost", "/api/v1/sync/push", "authorization"));
        assertFalse(preflight("https://localhost", "/api/v1/auth/web/prepare", "X-Carrim-Web"));
        assertFalse(preflight("https://localhost", "/api/v1/photo-admin/photos", "X-Carrim-Photo-Admin"));
        assertFalse(preflight("https://localhost", "/api/v1/sync/push", "X-Carrim-Web"));
    }

    @Test
    void readinessDoesNotLeakDatabaseFailuresAndClosesConnection() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        var controller = new RenderHealthController(dataSource);
        var healthy = controller.health();
        assertEquals(200, healthy.getStatusCode().value());
        assertEquals(java.util.Map.of("status", "UP"), healthy.getBody());
        verify(connection).close();
        when(dataSource.getConnection()).thenThrow(new SQLException("password and host must not leak"));
        var failed = controller.health();
        assertEquals(503, failed.getStatusCode().value());
        assertEquals(java.util.Map.of("status", "DOWN"), failed.getBody());
        assertEquals("no-store", failed.getHeaders().getCacheControl());
    }
}

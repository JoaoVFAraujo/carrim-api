package br.com.carrim.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class PhotoAdminAccessTests {
    private static final String TOKEN = "A".repeat(43);

    private static String hash() throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.US_ASCII)));
    }

    private MockHttpServletRequest request() {
        var r = new MockHttpServletRequest();
        r.setRemoteAddr("127.0.0.1");
        r.addHeader("X-Carrim-Photo-Admin", TOKEN);
        return r;
    }

    @Test
    void disabledByDefaultAndDoesNotAcceptAnonymousCredentials() throws Exception {
        assertFalse(new PhotoAdminAccess("", new MockEnvironment()).allowed(request()));
        var r = request();
        r.removeHeader("X-Carrim-Photo-Admin");
        r.addHeader("Authorization", "Bearer " + TOKEN);
        assertFalse(new PhotoAdminAccess(hash(), new MockEnvironment()).allowed(r));
    }

    @Test
    void requiresCanonicalSecretAndHttpsOutsideLocalDevelopment() throws Exception {
        var dev = new PhotoAdminAccess(hash(), new MockEnvironment());
        assertTrue(dev.allowed(request()));
        var remote = request();
        remote.setRemoteAddr("192.0.2.1");
        assertFalse(dev.allowed(remote));
        var prod = new PhotoAdminAccess(hash(), new MockEnvironment().withProperty("unused", "true"));
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        prod = new PhotoAdminAccess(hash(), env);
        assertFalse(prod.allowed(request()));
        var secure = request();
        secure.setSecure(true);
        assertTrue(prod.allowed(secure));
        secure.addHeader("X-Carrim-Photo-Admin", TOKEN);
        assertFalse(prod.allowed(secure));
    }

    @Test
    void limitsFailedAttemptsByConnectionAddress() throws Exception {
        var access = new PhotoAdminAccess(hash(), new MockEnvironment());
        for (int n = 0; n < 10; n++) {
            var bad = request();
            bad.removeHeader("X-Carrim-Photo-Admin");
            assertFalse(access.allowed(bad));
        }
        assertTrue(access.allowed(request()));
    }
}

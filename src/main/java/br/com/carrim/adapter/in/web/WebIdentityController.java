package br.com.carrim.adapter.in.web;

import br.com.carrim.application.identity.BootstrapCommand;
import br.com.carrim.application.identity.IdentityService;
import br.com.carrim.application.identity.IssuedIdentity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Web proof is HttpOnly; only short-lived-in-memory Bearer credentials reach JavaScript. */
@RestController
public class WebIdentityController {
    private static final String COOKIE = "carrim_web_identity";
    private final IdentityService identity;
    private final boolean production;
    private final SecureRandom random = new SecureRandom();

    public WebIdentityController(IdentityService identity, Environment environment) {
        this.identity = identity;
        this.production = environment.acceptsProfiles(Profiles.of("prod"));
    }

    public record ExpectedInstallation(UUID installationId) {}

    @PostMapping(value = "/api/v1/auth/web/prepare", consumes = "application/json")
    public Map<String, UUID> prepare(
            @RequestBody ExpectedInstallation expected, HttpServletRequest request, HttpServletResponse response) {
        guard(request, response);
        String proof = cookie(request);
        if (proof == null) {
            if (expected.installationId() != null) throw denied(HttpStatus.CONFLICT);
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            proof = UUID.randomUUID() + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        UUID installation = installation(proof);
        if (expected.installationId() != null && !expected.installationId().equals(installation))
            throw denied(HttpStatus.CONFLICT);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(cookieName(request), proof)
                        .httpOnly(true)
                        .secure(request.isSecure())
                        .sameSite("Strict")
                        .path(request.isSecure() ? "/" : "/api/v1/auth/web")
                        .maxAge(Duration.ofDays(365))
                        .build()
                        .toString());
        // Browser must receive/persist this cookie BEFORE a server owner is created.
        return Map.of("installationId", installation);
    }

    @PostMapping(value = "/api/v1/auth/web/token", consumes = "application/json")
    public IssuedIdentity token(
            @RequestBody ExpectedInstallation expected, HttpServletRequest request, HttpServletResponse response) {
        guard(request, response);
        String proof = cookie(request);
        if (proof == null || expected.installationId() == null) throw denied(HttpStatus.UNAUTHORIZED);
        UUID installation = installation(proof);
        if (!installation.equals(expected.installationId())) throw denied(HttpStatus.CONFLICT);
        return identity.bootstrap(new BootstrapCommand(installation, proof.substring(37), "WEB", "0.0.2"));
    }

    private void guard(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        // Custom header requires a CORS preflight on foreign origins. This API permits no CORS.
        // Fetch Metadata also excludes sibling origins; native Bearer routes remain cookie-free.
        String origin = request.getHeader("Origin");
        if (origin != null) {
            try {
                URI uri = URI.create(origin);
                int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
                if (!request.getScheme().equals(uri.getScheme())
                        || !request.getServerName().equals(uri.getHost())
                        || request.getServerPort() != port
                        || uri.getUserInfo() != null
                        || uri.getRawQuery() != null
                        || uri.getRawFragment() != null
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()))
                    throw denied(HttpStatus.FORBIDDEN);
            } catch (IllegalArgumentException exception) {
                throw denied(HttpStatus.FORBIDDEN);
            }
        }
        String site = request.getHeader("Sec-Fetch-Site");
        if (java.util.Collections.list(request.getHeaders("Sec-Fetch-Site")).size() > 1
                || java.util.Collections.list(request.getHeaders("X-Carrim-Web"))
                                .size()
                        != 1
                || !"1".equals(request.getHeader("X-Carrim-Web"))
                || (site != null && !site.equals("same-origin"))) throw denied(HttpStatus.FORBIDDEN);
        if (!request.isSecure()
                && (production
                        || !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(request.getServerName())
                        || !Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())))
            throw denied(HttpStatus.FORBIDDEN);
    }

    private static String cookieName(HttpServletRequest request) {
        return request.isSecure() ? "__Host-" + COOKIE : COOKIE;
    }

    private static String cookie(HttpServletRequest request) {
        String value = null;
        if (request.getCookies() != null)
            for (Cookie cookie : request.getCookies()) {
                if (cookieName(request).equals(cookie.getName())) {
                    if (value != null) throw denied(HttpStatus.UNAUTHORIZED);
                    value = cookie.getValue();
                }
            }
        if (value != null) installation(value);
        return value;
    }

    private static UUID installation(String proof) {
        if (!proof.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]"))
            throw denied(HttpStatus.UNAUTHORIZED);
        return UUID.fromString(proof.substring(0, 36));
    }

    private static ResponseStatusException denied(HttpStatus status) {
        return new ResponseStatusException(status);
    }
}

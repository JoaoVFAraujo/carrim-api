package br.com.carrim.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.env.Environment;

/** Dedicated operator secret, never an anonymous-installation role or browser cookie. */
public final class PhotoAdminAccess {
    private final byte[] expected;
    private final boolean production;
    private final Map<String, ArrayDeque<Long>> failures = new HashMap<>();

    public PhotoAdminAccess(String hash, Environment env) {
        this.expected =
                hash != null && hash.matches("[0-9a-fA-F]{64}") ? HexFormat.of().parseHex(hash) : null;
        this.production = Arrays.asList(env.getActiveProfiles()).contains("prod");
    }

    public synchronized boolean allowed(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        failures.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(t -> t <= now - 60000);
            return entry.getValue().isEmpty();
        });
        String ip = request.getRemoteAddr();
        String header = request.getHeader("X-Carrim-Photo-Admin");
        boolean valid = false;
        try {
            boolean local = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(ip);
            var headers = request.getHeaders("X-Carrim-Photo-Admin");
            int count = Collections.list(headers).size();
            if (expected != null
                    && (request.isSecure() || !production && local)
                    && count == 1
                    && header != null
                    && header.matches("[A-Za-z0-9_-]{43}")) {
                byte[] decoded = Base64.getUrlDecoder().decode(header);
                if (decoded.length == 32
                        && Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(decoded)
                                .equals(header))
                    valid = MessageDigest.isEqual(
                            expected,
                            MessageDigest.getInstance("SHA-256").digest(header.getBytes(StandardCharsets.US_ASCII)));
            }
        } catch (Exception ignored) {
            valid = false;
        }
        if (valid) return true;
        var recent = failures.get(ip);
        if (recent != null && recent.size() >= 10 || !failures.containsKey(ip) && failures.size() >= 4096) return false;
        failures.computeIfAbsent(ip, k -> new ArrayDeque<>()).addLast(now);
        return false;
    }
}

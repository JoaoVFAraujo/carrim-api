package br.com.carrim.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Local baseline; source address comes from the connection, never an untrusted forwarded header. */
final class BootstrapRateFilter extends OncePerRequestFilter {
    static final RequestMatcher ROUTE =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/anonymous");
    private static final long WINDOW = 60_000_000_000L;
    private final Map<String, Bucket> buckets = new HashMap<>();

    private record Bucket(long start, int count) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ROUTE.matches(request);
    }

    private synchronized boolean allowed(String address) {
        long now = System.nanoTime();
        var bucket = buckets.get(address);
        if (bucket == null || now - bucket.start() >= WINDOW) {
            if (bucket == null && buckets.size() >= 4096) {
                buckets.entrySet().removeIf(entry -> now - entry.getValue().start() >= WINDOW);
                if (buckets.size() >= 4096) return false;
            }
            buckets.put(address, new Bucket(now, 1));
            return true;
        }
        if (bucket.count() >= 60) return false;
        buckets.put(address, new Bucket(bucket.start(), bucket.count() + 1));
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allowed(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter()
                    .write("{\"code\":\"BOOTSTRAP_RATE_LIMIT\",\"message\":\"Tente novamente em instantes.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

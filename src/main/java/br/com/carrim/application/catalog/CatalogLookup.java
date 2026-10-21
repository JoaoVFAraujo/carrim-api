package br.com.carrim.application.catalog;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Bounded single-process lookup cache. Only public provider replies are shared. */
public final class CatalogLookup {
    private record Cached(ProductLookup value, long until) {}

    private final CatalogRepository catalog;
    private final ExternalProductCatalog external;
    private final Clock clock;
    private final boolean enabled;
    private final Object lock = new Object();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<ProductLookup>> pending = new HashMap<>();
    private final Map<UUID, ArrayDeque<Long>> owners = new HashMap<>();
    private final ArrayDeque<Long> outbound = new ArrayDeque<>();
    private long cooldownUntil;

    public CatalogLookup(CatalogRepository catalog, ExternalProductCatalog external, Clock clock, boolean enabled) {
        this.catalog = catalog;
        this.external = external;
        this.clock = clock;
        this.enabled = enabled;
    }

    public ProductLookup lookup(UUID owner, String code) {
        if (owner == null || code == null || !code.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13})"))
            throw new IllegalArgumentException("Invalid lookup");
        synchronized (lock) {
            long now = clock.millis();
            owners.entrySet().removeIf(entry -> {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            });
            if (!owners.containsKey(owner) && owners.size() >= 4096)
                return ProductLookup.empty(code, ProductLookup.Status.RATE_LIMITED);
            var requests = owners.computeIfAbsent(owner, ignored -> new ArrayDeque<>());
            if (requests.size() >= 10) return ProductLookup.empty(code, ProductLookup.Status.RATE_LIMITED);
            requests.addLast(now);
        }
        var personal = catalog.productByBarcode(owner, code);
        if (personal.isPresent()) {
            var product = personal.get().value();
            return new ProductLookup(
                    ProductLookup.Status.FOUND,
                    ProductLookup.Source.PERSONAL,
                    code,
                    product.name(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    product.measurementType());
        }
        if (!enabled) return ProductLookup.empty(code, ProductLookup.Status.DISABLED);
        CompletableFuture<ProductLookup> future;
        boolean leader = false;
        synchronized (lock) {
            long now = clock.millis();
            cache.entrySet().removeIf(entry -> entry.getValue().until() <= now);
            var cached = cache.get(code);
            if (cached != null) return cached.value();
            future = pending.get(code);
            if (future == null) {
                if (now < cooldownUntil) return ProductLookup.empty(code, ProductLookup.Status.RATE_LIMITED);
                prune(outbound, now);
                if (outbound.size() >= 10) return ProductLookup.empty(code, ProductLookup.Status.RATE_LIMITED);
                if (pending.size() >= 2) return ProductLookup.empty(code, ProductLookup.Status.UNAVAILABLE);
                outbound.addLast(now);
                future = new CompletableFuture<>();
                pending.put(code, future);
                leader = true;
            }
        }
        if (!leader) {
            try {
                return future.get(6, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                /* Provider failure remains recoverable through manual entry. */
            }
            return ProductLookup.empty(code, ProductLookup.Status.UNAVAILABLE);
        }
        ProductLookup result;
        int retryAfter = 0;
        try {
            var reply = external.lookup(code);
            result = reply.product();
            retryAfter = reply.retryAfterSeconds();
        } catch (RuntimeException ignored) {
            result = ProductLookup.empty(code, ProductLookup.Status.UNAVAILABLE);
        }
        synchronized (lock) {
            long now = clock.millis();
            if (result.status() == ProductLookup.Status.FOUND || result.status() == ProductLookup.Status.NOT_FOUND) {
                if (cache.size() >= 512) cache.remove(cache.keySet().iterator().next());
                long ttl = result.status() == ProductLookup.Status.FOUND
                        ? Duration.ofHours(24).toMillis()
                        : Duration.ofMinutes(15).toMillis();
                cache.put(code, new Cached(result, now + ttl));
            } else if (retryAfter > 0) {
                cooldownUntil = Math.max(cooldownUntil, now + Math.clamp(retryAfter, 60, 300) * 1000L);
            }
            pending.remove(code);
            future.complete(result);
        }
        return result;
    }

    private static void prune(ArrayDeque<Long> requests, long now) {
        while (!requests.isEmpty() && requests.peekFirst() <= now - 60000) requests.removeFirst();
    }
}

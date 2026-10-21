package br.com.carrim.adapter.in.web;

import br.com.carrim.application.photos.PhotoLimitException;
import java.time.Clock;
import java.util.*;

public final class PhotoRateLimit {
    private final Map<UUID, ArrayDeque<Long>> buckets = new HashMap<>();
    private final Clock clock;

    public PhotoRateLimit(Clock clock) {
        this.clock = clock;
    }

    public synchronized void write(UUID owner) {
        long now = clock.millis();
        buckets.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(at -> at <= now - 60000);
            return entry.getValue().isEmpty();
        });
        if (!buckets.containsKey(owner) && buckets.size() >= 4096) throw new PhotoLimitException();
        var times = buckets.computeIfAbsent(owner, k -> new ArrayDeque<>());
        if (times.size() >= 20) throw new PhotoLimitException();
        times.addLast(now);
    }
}

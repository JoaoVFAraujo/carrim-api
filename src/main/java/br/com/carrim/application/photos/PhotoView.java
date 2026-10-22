package br.com.carrim.application.photos;

import java.time.Instant;
import java.util.UUID;

public record PhotoView(
        UUID id,
        String barcode,
        UUID itemId,
        String name,
        String source,
        String status,
        long version,
        String contentHash,
        String sourceUrl,
        String licenseUrl,
        Instant updatedAt,
        boolean own) {}

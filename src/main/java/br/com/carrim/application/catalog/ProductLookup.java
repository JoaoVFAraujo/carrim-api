package br.com.carrim.application.catalog;

import br.com.carrim.domain.shopping.MeasurementType;

/** A read-only suggestion. Confirmation uses the existing product/outbox write path. */
public record ProductLookup(
        Status status,
        Source source,
        String barcode,
        String name,
        String brand,
        String quantity,
        String imageUrl,
        String sourceUrl,
        String licenseUrl,
        MeasurementType measurementType) {
    public enum Status {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE,
        RATE_LIMITED,
        DISABLED
    }

    public enum Source {
        PERSONAL,
        OPEN_FOOD_FACTS
    }

    public static ProductLookup empty(String barcode, Status status) {
        return new ProductLookup(status, null, barcode, null, null, null, null, null, null, null);
    }
}

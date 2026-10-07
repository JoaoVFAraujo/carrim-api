package br.com.carrim.domain.catalog;

import br.com.carrim.domain.shopping.MeasurementType;
import java.util.Objects;
import java.util.UUID;

public record Product(UUID id, String name, String barcode, MeasurementType measurementType) {
    public Product {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalArgumentException("Product name must contain 1 to 120 characters");
        }
        Objects.requireNonNull(measurementType, "measurementType");
        if (barcode != null) {
            barcode = barcode.strip();
            if (!barcode.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13})")) {
                throw new IllegalArgumentException("Barcode must contain 8, 12, or 13 digits");
            }
        }
    }

    public Product rename(String nextName) {
        return new Product(id, nextName, barcode, measurementType);
    }
}

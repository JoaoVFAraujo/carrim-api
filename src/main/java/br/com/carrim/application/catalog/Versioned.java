package br.com.carrim.application.catalog;

import java.util.Objects;

public record Versioned<T>(T value, long version) {
    public Versioned {
        Objects.requireNonNull(value, "value");
        if (version < 0) {
            throw new IllegalArgumentException("Version must not be negative");
        }
    }
}

package br.com.carrim.domain.supermarket;

import java.util.Objects;
import java.util.UUID;

public record Supermarket(UUID id, String name) {
    public Supermarket {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalArgumentException("Supermarket name must contain 1 to 120 characters");
        }
    }

    public Supermarket rename(String nextName) {
        return new Supermarket(id, nextName);
    }
}

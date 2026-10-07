package br.com.carrim.domain.shared;

import java.util.Objects;

/** BRL represented in exact cents. Signed values support balances and checkout differences. */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public Money times(long factor) {
        return new Money(Math.multiplyExact(cents, factor));
    }

    @Override
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "other");
        return Long.compare(cents, other.cents);
    }
}

package br.com.carrim.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoneyTests {

    @Test
    void addsCentsExactly() {
        assertEquals(new Money(30), new Money(10).plus(new Money(20)));
        assertEquals(new Money(1598), new Money(799).times(2));
        assertEquals(Money.ZERO, new Money(799).times(0));
    }

    @Test
    void preservesNegativeBalancesAndCheckoutDifferences() {
        assertEquals(new Money(-175), new Money(2400).minus(new Money(2575)));
        assertEquals(new Money(175), new Money(2575).minus(new Money(2400)));
        assertEquals(Money.ZERO, new Money(-175).plus(new Money(175)));
    }

    @Test
    void rejectsOverflowInsteadOfWrappingMoney() {
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).plus(new Money(1)));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MIN_VALUE).minus(new Money(1)));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).times(2));
    }

    @Test
    void comparesExtremeValuesWithoutSubtractionOverflow() {
        assertTrue(new Money(Long.MIN_VALUE).compareTo(new Money(Long.MAX_VALUE)) < 0);
        assertEquals(0, new Money(100).compareTo(new Money(100)));
    }

    @Test
    void rejectsMissingOperands() {
        assertThrows(NullPointerException.class, () -> Money.ZERO.plus(null));
        assertThrows(NullPointerException.class, () -> Money.ZERO.minus(null));
        assertThrows(NullPointerException.class, () -> Money.ZERO.compareTo(null));
    }
}

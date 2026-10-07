package br.com.carrim.domain.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.carrim.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemSubtotalTests {

    @Test
    void unitPricesAndMaximumQuantitiesMatchTheMobile() {
        assertEquals(new Money(1598), ItemSubtotal.unit(new Money(799), 2));
        assertEquals(new Money(999_900_000_000L), ItemSubtotal.unit(new Money(100_000_000), 9999));
    }

    @ParameterizedTest
    @CsvSource({"699,824,576", "699,500,350", "1,499,0", "1,500,1", "100000000,9999999,999999900000"})
    void weightedLinesRoundHalfUpInIntegerCents(long price, int grams, long expected) {
        assertEquals(new Money(expected), ItemSubtotal.weight(new Money(price), grams));
    }

    @Test
    void roundsEachLineBeforeSummingTheShoppingTotal() {
        Money line = ItemSubtotal.weight(new Money(1), 500);
        assertEquals(new Money(2), line.plus(line));
    }

    @Test
    void bundlePricesUseCompleteGroupsInsteadOfRoundedUnitPrices() {
        assertEquals(new Money(2000), ItemSubtotal.bundle(new Money(1000), 3, 6));
        assertEquals(new Money(1), ItemSubtotal.bundle(new Money(1), 9999, 9999));
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.bundle(new Money(1000), 3, 4));
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.bundle(new Money(1000), 1, 4));
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.bundle(new Money(1000), 0, 4));
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.bundle(new Money(1000), 10_000, 4));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 100_000_001, Long.MAX_VALUE})
    void rejectsInvalidReferencePricesForEveryMeasurement(long cents) {
        Money price = new Money(cents);
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.unit(price, 1));
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.weight(price, 1));
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.bundle(price, 2, 2));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 10_000, Integer.MAX_VALUE})
    void rejectsInvalidQuantities(int quantity) {
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.unit(new Money(1), quantity));
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.bundle(new Money(1), 2, quantity));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 10_000_000, Integer.MAX_VALUE})
    void rejectsInvalidWeights(int grams) {
        assertThrows(IllegalArgumentException.class, () -> ItemSubtotal.weight(new Money(1), grams));
    }

    @Test
    void rejectsMissingReferencePrices() {
        assertThrows(NullPointerException.class, () -> ItemSubtotal.unit(null, 1));
        assertThrows(NullPointerException.class, () -> ItemSubtotal.weight(null, 1));
        assertThrows(NullPointerException.class, () -> ItemSubtotal.bundle(null, 2, 2));
    }
}

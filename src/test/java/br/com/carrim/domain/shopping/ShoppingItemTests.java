package br.com.carrim.domain.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.carrim.domain.shared.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShoppingItemTests {
    private final UUID sessionId = UUID.randomUUID();

    private ShoppingItem item(
            MeasurementType measure, PricingType pricing, int quantity, Integer grams, Integer group) {
        return new ShoppingItem(
                UUID.randomUUID(),
                sessionId,
                null,
                " Banana ",
                measure,
                pricing,
                new Money(699),
                quantity,
                grams,
                group);
    }

    @Test
    void preservesManualNameSnapshotAndDerivesUnitSubtotal() {
        ShoppingItem item = item(MeasurementType.UNIT, PricingType.REGULAR, 2, null, null);
        assertEquals("Banana", item.productNameSnapshot());
        assertNull(item.productId());
        assertEquals(new Money(1398), item.subtotal());
    }

    @Test
    void derivesWeightAndBundleSubtotals() {
        assertEquals(
                new Money(576),
                item(MeasurementType.WEIGHT, PricingType.REGULAR, 1, 824, null).subtotal());
        assertEquals(
                new Money(1398),
                item(MeasurementType.UNIT, PricingType.BUNDLE, 6, null, 3).subtotal());
    }

    @Test
    void refusesIncompleteBundlesAndMixedMeasurementFields() {
        assertThrows(IllegalArgumentException.class, () -> item(MeasurementType.UNIT, PricingType.BUNDLE, 4, null, 3));
        assertThrows(IllegalArgumentException.class, () -> item(MeasurementType.WEIGHT, PricingType.BUNDLE, 1, 500, 3));
        assertThrows(
                IllegalArgumentException.class, () -> item(MeasurementType.WEIGHT, PricingType.REGULAR, 2, 500, null));
        assertThrows(
                IllegalArgumentException.class, () -> item(MeasurementType.WEIGHT, PricingType.REGULAR, 1, null, null));
        assertThrows(
                IllegalArgumentException.class, () -> item(MeasurementType.WEIGHT, PricingType.REGULAR, 1, 500, 3));
        assertThrows(
                IllegalArgumentException.class, () -> item(MeasurementType.UNIT, PricingType.REGULAR, 1, 500, null));
        assertThrows(IllegalArgumentException.class, () -> item(MeasurementType.UNIT, PricingType.REGULAR, 1, null, 3));
        assertThrows(
                IllegalArgumentException.class, () -> item(MeasurementType.UNIT, PricingType.BUNDLE, 3, null, null));
    }

    @Test
    void validatesIdentityAndSnapshotName() {
        for (String name : new String[] {" ", "a".repeat(121)}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ShoppingItem(
                            UUID.randomUUID(),
                            sessionId,
                            null,
                            name,
                            MeasurementType.UNIT,
                            PricingType.REGULAR,
                            new Money(1),
                            1,
                            null,
                            null));
        }
        assertThrows(
                NullPointerException.class,
                () -> new ShoppingItem(
                        null,
                        sessionId,
                        null,
                        "Coffee",
                        MeasurementType.UNIT,
                        PricingType.REGULAR,
                        new Money(1),
                        1,
                        null,
                        null));
        assertThrows(
                NullPointerException.class,
                () -> new ShoppingItem(
                        UUID.randomUUID(),
                        null,
                        null,
                        "Coffee",
                        MeasurementType.UNIT,
                        PricingType.REGULAR,
                        new Money(1),
                        1,
                        null,
                        null));
    }
}

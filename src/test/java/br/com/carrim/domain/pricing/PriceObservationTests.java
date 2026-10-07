package br.com.carrim.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shared.Money;
import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.domain.shopping.PricingType;
import br.com.carrim.domain.shopping.ShoppingItem;
import br.com.carrim.domain.shopping.ShoppingSession;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriceObservationTests {
    private final UUID sessionId = UUID.randomUUID();
    private final UUID marketId = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-10-07T15:00:00Z");
    private final Instant finish = start.plusSeconds(60);

    private ShoppingItem item(
            MeasurementType measure, PricingType pricing, long cents, int qty, Integer grams, Integer group) {
        return new ShoppingItem(
                UUID.randomUUID(), sessionId, null, "Snapshot", measure, pricing, new Money(cents), qty, grams, group);
    }

    private ShoppingSession active(ShoppingItem item) {
        return ShoppingSession.start(sessionId, marketId, null, start).addItem(item);
    }

    @Test
    void refusesActiveAndCanceledSessions() {
        ShoppingSession active = active(item(MeasurementType.UNIT, PricingType.REGULAR, 799, 2, null, null));
        assertThrows(IllegalStateException.class, () -> PriceObservation.fromCompletedSession(active));
        assertThrows(IllegalStateException.class, () -> PriceObservation.fromCompletedSession(active.cancel(finish)));
        assertThrows(NullPointerException.class, () -> PriceObservation.fromCompletedSession(null));
    }

    @Test
    void preservesTheFinalEditedSnapshotAndStableObservationIdentity() {
        ShoppingItem original = item(MeasurementType.UNIT, PricingType.REGULAR, 799, 1, null, null);
        ShoppingItem edited = new ShoppingItem(
                original.id(),
                sessionId,
                null,
                "Final name",
                MeasurementType.UNIT,
                PricingType.REGULAR,
                new Money(899),
                2,
                null,
                null);
        ShoppingSession completed = active(original).replaceItem(edited).complete(finish, null);
        var observations = PriceObservation.fromCompletedSession(completed);
        PriceObservation observation = observations.getFirst();
        assertEquals(1, observations.size());
        assertEquals(original.id(), observation.id());
        assertEquals(sessionId, observation.sessionId());
        assertEquals(marketId, observation.supermarketId());
        assertEquals(finish, observation.observedAt());
        assertEquals(edited, observation.item());
        assertEquals(new Money(899), observation.normalizedPrice());
        assertFalse(observation.normalizedPriceIsApproximate());
        assertEquals(
                observation.id(),
                PriceObservation.fromCompletedSession(completed).getFirst().id());
        assertNull(observation.item().productId());
        assertThrows(UnsupportedOperationException.class, observations::clear);
    }

    @Test
    void keepsKilogramPricesSeparateFromUnitSubtotals() {
        ShoppingItem weight = item(MeasurementType.WEIGHT, PricingType.REGULAR, 699, 1, 824, null);
        PriceObservation observation = PriceObservation.fromCompletedSession(
                        active(weight).complete(finish, null))
                .getFirst();
        assertEquals(ComparisonBasis.KG, observation.comparisonBasis());
        assertEquals(new Money(699), observation.normalizedPrice());
        assertEquals(new Money(576), observation.item().subtotal());
        assertFalse(observation.normalizedPriceIsApproximate());
    }

    @Test
    void roundsBundleEquivalenceWithoutLosingTheOriginalGroupPrice() {
        ShoppingItem bundle = item(MeasurementType.UNIT, PricingType.BUNDLE, 1000, 6, null, 3);
        PriceObservation observation = PriceObservation.fromCompletedSession(
                        active(bundle).complete(finish, null))
                .getFirst();
        assertEquals(ComparisonBasis.UNIT, observation.comparisonBasis());
        assertEquals(new Money(333), observation.normalizedPrice());
        assertTrue(observation.normalizedPriceIsApproximate());
        assertEquals(new Money(1000), observation.item().referencePrice());
        assertEquals(new Money(2000), observation.item().subtotal());
        ShoppingItem halfCent = item(MeasurementType.UNIT, PricingType.BUNDLE, 1, 2, null, 2);
        assertEquals(
                new Money(1),
                PriceObservation.fromCompletedSession(active(halfCent).complete(finish, null))
                        .getFirst()
                        .normalizedPrice());
        ShoppingItem tiny = item(MeasurementType.UNIT, PricingType.BUNDLE, 1, 3, null, 3);
        assertEquals(
                Money.ZERO,
                PriceObservation.fromCompletedSession(active(tiny).complete(finish, null))
                        .getFirst()
                        .normalizedPrice());
        ShoppingItem exact = item(MeasurementType.UNIT, PricingType.BUNDLE, 1000, 2, null, 2);
        assertFalse(PriceObservation.fromCompletedSession(active(exact).complete(finish, null))
                .getFirst()
                .normalizedPriceIsApproximate());
    }

    @Test
    void catalogRenameCannotChangeHistoricalItemNames() {
        Product product = new Product(UUID.randomUUID(), "Coffee", "12345678", MeasurementType.UNIT);
        ShoppingItem line = new ShoppingItem(
                UUID.randomUUID(),
                sessionId,
                product.id(),
                product.name(),
                MeasurementType.UNIT,
                PricingType.REGULAR,
                new Money(799),
                1,
                null,
                null);
        var observation = PriceObservation.fromCompletedSession(active(line).complete(finish, null))
                .getFirst();
        assertEquals("Renamed", product.rename("Renamed").name());
        assertEquals("Coffee", observation.item().productNameSnapshot());
        assertEquals(product.id(), observation.item().productId());
    }

    @Test
    void createsOneObservationPerLineEvenForTheSameProduct() {
        ShoppingItem unit = item(MeasurementType.UNIT, PricingType.REGULAR, 799, 1, null, null);
        ShoppingItem bundle = item(MeasurementType.UNIT, PricingType.BUNDLE, 1000, 3, null, 3);
        var completed = active(unit).addItem(bundle).complete(finish, null);
        assertEquals(2, PriceObservation.fromCompletedSession(completed).size());
    }
}

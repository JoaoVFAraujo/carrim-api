package br.com.carrim.domain.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.carrim.domain.shared.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShoppingSessionTests {
    private final UUID id = UUID.randomUUID();
    private final UUID market = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-10-07T12:00:00Z");
    private final Instant finish = start.plusSeconds(60);

    private ShoppingSession session() {
        return ShoppingSession.start(id, market, null, start);
    }

    private ShoppingItem unit(UUID itemId, int quantity) {
        return new ShoppingItem(
                itemId,
                id,
                null,
                "Coffee",
                MeasurementType.UNIT,
                PricingType.REGULAR,
                new Money(799),
                quantity,
                null,
                null);
    }

    @Test
    void derivesMixedTotalAndNegativeBudgetWithoutChangingOriginal() {
        ShoppingSession original = session();
        ShoppingItem weight = new ShoppingItem(
                UUID.randomUUID(),
                id,
                null,
                "Banana",
                MeasurementType.WEIGHT,
                PricingType.REGULAR,
                new Money(699),
                1,
                824,
                null);
        ShoppingItem bundle = new ShoppingItem(
                UUID.randomUUID(),
                id,
                null,
                "Milk",
                MeasurementType.UNIT,
                PricingType.BUNDLE,
                new Money(1000),
                6,
                null,
                3);
        ShoppingSession next = original.addItem(unit(UUID.randomUUID(), 2))
                .addItem(weight)
                .addItem(bundle)
                .changeBudget(new Money(4000));
        assertEquals(Money.ZERO, original.total());
        assertNull(original.remainingBudget());
        assertEquals(new Money(4174), next.total());
        assertEquals(new Money(-174), next.remainingBudget());
        assertNull(next.changeBudget(null).remainingBudget());
    }

    @Test
    void replacesAndRemovesAnExistingLineByClientIdentity() {
        UUID lineId = UUID.randomUUID();
        ShoppingSession initial = session().addItem(unit(lineId, 1));
        ShoppingSession edited = initial.replaceItem(unit(lineId, 2));
        assertEquals(new Money(799), initial.total());
        assertEquals(new Money(1598), edited.total());
        assertEquals(Money.ZERO, edited.removeItem(lineId).total());
        assertThrows(IllegalArgumentException.class, () -> edited.removeItem(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> initial.replaceItem(unit(UUID.randomUUID(), 2)));
    }

    @Test
    void rejectsDuplicateAndForeignLineIdsWithoutChangingSession() {
        ShoppingItem line = unit(UUID.randomUUID(), 1);
        ShoppingSession initial = session().addItem(line);
        assertThrows(IllegalArgumentException.class, () -> initial.addItem(line));
        ShoppingItem foreign = new ShoppingItem(
                line.id(),
                UUID.randomUUID(),
                null,
                "Coffee",
                MeasurementType.UNIT,
                PricingType.REGULAR,
                new Money(799),
                1,
                null,
                null);
        assertThrows(IllegalArgumentException.class, () -> initial.addItem(foreign));
        assertThrows(IllegalArgumentException.class, () -> initial.replaceItem(foreign));
        assertEquals(List.of(line), initial.items());
    }

    @Test
    void finalizesWithoutAlteringItemsAndComparesCheckout() {
        ShoppingSession active = session().addItem(unit(UUID.randomUUID(), 2));
        ShoppingSession completed = active.complete(finish, new Money(1700));
        assertEquals(ShoppingStatus.ACTIVE, active.status());
        assertEquals(ShoppingStatus.COMPLETED, completed.status());
        assertEquals(finish, completed.finishedAt());
        assertEquals(active.items(), completed.items());
        assertEquals(new Money(102), completed.checkoutDifference());
        assertEquals(new Money(-1598), active.complete(finish, Money.ZERO).checkoutDifference());
        assertNull(active.complete(finish, null).checkoutDifference());
        assertEquals(new Money(1598), completed.total());
    }

    @Test
    void refusesEveryMutationAfterCompletionOrCancellation() {
        ShoppingItem line = unit(UUID.randomUUID(), 1);
        ShoppingSession active = session().addItem(line);
        for (ShoppingSession terminal : List.of(active.complete(finish, null), active.cancel(finish))) {
            assertThrows(IllegalStateException.class, () -> terminal.addItem(unit(UUID.randomUUID(), 1)));
            assertThrows(IllegalStateException.class, () -> terminal.replaceItem(line));
            assertThrows(IllegalStateException.class, () -> terminal.removeItem(line.id()));
            assertThrows(IllegalStateException.class, () -> terminal.changeBudget(new Money(1)));
            assertThrows(IllegalStateException.class, () -> terminal.complete(finish, null));
            assertThrows(IllegalStateException.class, () -> terminal.cancel(finish));
        }
    }

    @Test
    void rejectsInvalidCompletionAndAllowsEmptyCancellation() {
        ShoppingSession empty = session();
        assertThrows(IllegalArgumentException.class, () -> empty.complete(finish, null));
        ShoppingSession active = empty.addItem(unit(UUID.randomUUID(), 1));
        assertThrows(IllegalArgumentException.class, () -> active.complete(start.minusSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> active.complete(null, null));
        assertThrows(IllegalArgumentException.class, () -> active.complete(finish, new Money(-1)));
        assertThrows(IllegalArgumentException.class, () -> active.complete(finish, new Money(100_000_001)));
        assertThrows(IllegalArgumentException.class, () -> empty.cancel(start.minusSeconds(1)));
        assertEquals(ShoppingStatus.CANCELED, empty.cancel(finish).status());
        assertEquals(ShoppingStatus.ACTIVE, active.status());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 100_000_001})
    void refusesInvalidBudgets(long cents) {
        assertThrows(IllegalArgumentException.class, () -> ShoppingSession.start(id, market, new Money(cents), start));
        assertThrows(IllegalArgumentException.class, () -> session().changeBudget(new Money(cents)));
    }

    @Test
    void protectsSnapshotsFromExternalListMutation() {
        var source = new ArrayList<ShoppingItem>();
        source.add(unit(UUID.randomUUID(), 1));
        ShoppingSession restored =
                new ShoppingSession(id, market, null, ShoppingStatus.ACTIVE, start, null, null, source);
        source.clear();
        assertEquals(1, restored.items().size());
        assertThrows(UnsupportedOperationException.class, () -> restored.items().clear());
    }

    @Test
    void validatesRestoredTerminalAndActiveState() {
        ShoppingItem line = unit(UUID.randomUUID(), 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShoppingSession(id, market, null, ShoppingStatus.ACTIVE, start, finish, null, List.of(line)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShoppingSession(
                        id, market, null, ShoppingStatus.ACTIVE, start, null, Money.ZERO, List.of(line)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShoppingSession(
                        id, market, null, ShoppingStatus.CANCELED, start, finish, Money.ZERO, List.of(line)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShoppingSession(id, market, null, ShoppingStatus.COMPLETED, start, finish, null, List.of()));
    }
}

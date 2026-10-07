package br.com.carrim.domain.shopping;

import br.com.carrim.domain.shared.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable aggregate: only active sessions accept mutations. IDs are supplied by the client. */
public record ShoppingSession(
        UUID id,
        UUID supermarketId,
        Money budget,
        ShoppingStatus status,
        Instant startedAt,
        Instant finishedAt,
        Money checkoutTotal,
        List<ShoppingItem> items) {

    public ShoppingSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(supermarketId, "supermarketId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        items = List.copyOf(items);
        if (budget != null && (budget.cents() < 1 || budget.cents() > 100_000_000)) {
            throw new IllegalArgumentException("Budget must be between 1 and 100000000 cents");
        }
        if (checkoutTotal != null && checkoutTotal.cents() < 0) {
            throw new IllegalArgumentException("Checkout total must not be negative");
        }
        if (status == ShoppingStatus.ACTIVE) {
            if (finishedAt != null || checkoutTotal != null) {
                throw new IllegalArgumentException("Active sessions cannot have completion data");
            }
        } else {
            if (finishedAt == null || finishedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("Finished time must be at or after start");
            }
            if (status == ShoppingStatus.COMPLETED && items.isEmpty()) {
                throw new IllegalArgumentException("Completed sessions require at least one item");
            }
            if (status == ShoppingStatus.CANCELED && checkoutTotal != null) {
                throw new IllegalArgumentException("Canceled sessions cannot have a checkout total");
            }
        }
        var ids = new HashSet<UUID>();
        for (ShoppingItem item : items) {
            if (!id.equals(item.sessionId()) || !ids.add(item.id())) {
                throw new IllegalArgumentException("Items must belong to this session and have unique IDs");
            }
        }
    }

    public static ShoppingSession start(UUID id, UUID supermarketId, Money budget, Instant startedAt) {
        return new ShoppingSession(id, supermarketId, budget, ShoppingStatus.ACTIVE, startedAt, null, null, List.of());
    }

    public Money total() {
        Money total = Money.ZERO;
        for (ShoppingItem item : items) {
            total = total.plus(item.subtotal());
        }
        return total;
    }

    public Money remainingBudget() {
        return budget == null ? null : budget.minus(total());
    }

    public Money checkoutDifference() {
        return checkoutTotal == null ? null : checkoutTotal.minus(total());
    }

    public ShoppingSession addItem(ShoppingItem item) {
        requireActive();
        var next = new ArrayList<>(items);
        next.add(item);
        return copy(budget, status, finishedAt, checkoutTotal, next);
    }

    public ShoppingSession replaceItem(ShoppingItem item) {
        requireActive();
        Objects.requireNonNull(item, "item");
        var next = new ArrayList<>(items);
        int index = indexOf(item.id());
        next.set(index, item);
        return copy(budget, status, finishedAt, checkoutTotal, next);
    }

    public ShoppingSession removeItem(UUID itemId) {
        requireActive();
        var next = new ArrayList<>(items);
        next.remove(indexOf(itemId));
        return copy(budget, status, finishedAt, checkoutTotal, next);
    }

    public ShoppingSession changeBudget(Money nextBudget) {
        requireActive();
        return copy(nextBudget, status, finishedAt, checkoutTotal, items);
    }

    public ShoppingSession complete(Instant completedAt, Money nextCheckoutTotal) {
        requireActive();
        return copy(budget, ShoppingStatus.COMPLETED, completedAt, nextCheckoutTotal, items);
    }

    public ShoppingSession cancel(Instant canceledAt) {
        requireActive();
        return copy(budget, ShoppingStatus.CANCELED, canceledAt, null, items);
    }

    private int indexOf(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId");
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id().equals(itemId)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Item not found in this session");
    }

    private void requireActive() {
        if (status != ShoppingStatus.ACTIVE) {
            throw new IllegalStateException("Only active sessions can be changed");
        }
    }

    private ShoppingSession copy(
            Money nextBudget,
            ShoppingStatus nextStatus,
            Instant nextFinishedAt,
            Money nextCheckoutTotal,
            List<ShoppingItem> nextItems) {
        return new ShoppingSession(
                id, supermarketId, nextBudget, nextStatus, startedAt, nextFinishedAt, nextCheckoutTotal, nextItems);
    }
}

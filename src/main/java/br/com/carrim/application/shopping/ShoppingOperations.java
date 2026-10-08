package br.com.carrim.application.shopping;

import br.com.carrim.domain.shared.Money;
import br.com.carrim.domain.shopping.ShoppingItem;
import br.com.carrim.domain.shopping.ShoppingSession;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Use cases delegate the transaction boundary to the aggregate port. Owner comes from server identity. */
public final class ShoppingOperations {
    private final ShoppingRepository repository;

    public ShoppingOperations(ShoppingRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public StoredShopping start(UUID owner, UUID id, UUID market, Money budget, Instant startedAt) {
        return repository.create(owner, ShoppingSession.start(id, market, budget, startedAt));
    }

    public StoredShopping addItem(UUID owner, UUID id, long version, ShoppingItem item) {
        return repository.mutate(owner, id, version, session -> session.addItem(item));
    }

    public StoredShopping replaceItem(UUID owner, UUID id, long version, ShoppingItem item) {
        return repository.mutate(owner, id, version, session -> session.replaceItem(item));
    }

    public StoredShopping removeItem(UUID owner, UUID id, long version, UUID itemId) {
        return repository.mutate(owner, id, version, session -> session.removeItem(itemId));
    }

    public StoredShopping changeBudget(UUID owner, UUID id, long version, Money budget) {
        return repository.mutate(owner, id, version, session -> session.changeBudget(budget));
    }

    public StoredShopping complete(UUID owner, UUID id, long version, Instant finishedAt, Money checkout) {
        return repository.mutate(owner, id, version, session -> session.complete(finishedAt, checkout));
    }

    public StoredShopping cancel(UUID owner, UUID id, long version, Instant finishedAt) {
        return repository.mutate(owner, id, version, session -> session.cancel(finishedAt));
    }
}

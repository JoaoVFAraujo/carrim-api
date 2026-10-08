package br.com.carrim.application.shopping;

import br.com.carrim.domain.shopping.ShoppingSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface ShoppingRepository {
    StoredShopping create(UUID owner, ShoppingSession session);

    Optional<StoredShopping> find(UUID owner, UUID sessionId);

    StoredShopping mutate(UUID owner, UUID sessionId, long expectedVersion, UnaryOperator<ShoppingSession> change);

    List<PriceHistoryEntry> history(UUID owner, UUID sessionId);
}

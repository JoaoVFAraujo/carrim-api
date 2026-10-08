package br.com.carrim.application.shopping;

import br.com.carrim.domain.shared.Money;
import java.time.Instant;
import java.util.UUID;

public interface CompletionRepository {
    StoredShopping complete(UUID owner, UUID session, long version, Instant finishedAt, Money checkout, UUID key);
}

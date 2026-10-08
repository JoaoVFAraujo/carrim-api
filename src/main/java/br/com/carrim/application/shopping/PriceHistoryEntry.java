package br.com.carrim.application.shopping;

import br.com.carrim.domain.pricing.ComparisonBasis;
import br.com.carrim.domain.shared.Money;
import br.com.carrim.domain.shopping.ShoppingItem;
import java.time.Instant;
import java.util.UUID;

public record PriceHistoryEntry(
        UUID id,
        UUID sessionId,
        UUID supermarketId,
        Instant observedAt,
        ShoppingItem item,
        ComparisonBasis comparisonBasis,
        Money normalizedPrice) {}

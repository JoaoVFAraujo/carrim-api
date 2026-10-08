package br.com.carrim.application.shopping;

import br.com.carrim.domain.shopping.ShoppingStatus;
import java.time.Instant;
import java.util.UUID;

public record ShoppingSummary(
        UUID id,
        UUID supermarketId,
        ShoppingStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long budgetCents,
        Long checkoutTotalCents,
        long calculatedTotalCents,
        long itemCount,
        long version) {}

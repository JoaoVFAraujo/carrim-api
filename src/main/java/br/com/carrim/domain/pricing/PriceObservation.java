package br.com.carrim.domain.pricing;

import br.com.carrim.domain.shared.Money;
import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.domain.shopping.PricingType;
import br.com.carrim.domain.shopping.ShoppingItem;
import br.com.carrim.domain.shopping.ShoppingSession;
import br.com.carrim.domain.shopping.ShoppingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A historical snapshot can only be derived from a completed session, never an active price edit. */
public final class PriceObservation {
    private final UUID sessionId;
    private final UUID supermarketId;
    private final Instant observedAt;
    private final ShoppingItem item;

    private PriceObservation(ShoppingSession session, ShoppingItem item) {
        this.sessionId = session.id();
        this.supermarketId = session.supermarketId();
        this.observedAt = session.finishedAt();
        this.item = item;
    }

    public static List<PriceObservation> fromCompletedSession(ShoppingSession session) {
        Objects.requireNonNull(session, "session");
        if (session.status() != ShoppingStatus.COMPLETED) {
            throw new IllegalStateException("Only completed sessions produce price observations");
        }
        return session.items().stream()
                .map(item -> new PriceObservation(session, item))
                .toList();
    }

    /** The globally unique line ID also makes repeated derivation stable for future persistence. */
    public UUID id() {
        return item.id();
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID supermarketId() {
        return supermarketId;
    }

    public Instant observedAt() {
        return observedAt;
    }

    public ShoppingItem item() {
        return item;
    }

    public ComparisonBasis comparisonBasis() {
        return item.measurementType() == MeasurementType.WEIGHT ? ComparisonBasis.KG : ComparisonBasis.UNIT;
    }

    /** Bundle equivalence is informational; basket totals always retain the exact group price. */
    public Money normalizedPrice() {
        if (item.pricingType() != PricingType.BUNDLE) {
            return item.referencePrice();
        }
        long cents = BigDecimal.valueOf(item.referencePrice().cents())
                .divide(BigDecimal.valueOf(item.bundleQuantity()), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Money(cents);
    }

    public boolean normalizedPriceIsApproximate() {
        return item.pricingType() == PricingType.BUNDLE && item.referencePrice().cents() % item.bundleQuantity() != 0;
    }
}

package br.com.carrim.domain.shopping;

import br.com.carrim.domain.shared.Money;
import java.util.Objects;
import java.util.UUID;

/** Immutable line snapshot. Reference price represents a unit, kilogram, or complete bundle. */
public record ShoppingItem(
        UUID id,
        UUID sessionId,
        UUID productId,
        String productNameSnapshot,
        MeasurementType measurementType,
        PricingType pricingType,
        Money referencePrice,
        int quantity,
        Integer weightGrams,
        Integer bundleQuantity) {

    public ShoppingItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        productNameSnapshot = Objects.requireNonNull(productNameSnapshot, "productNameSnapshot")
                .strip();
        if (productNameSnapshot.isEmpty() || productNameSnapshot.length() > 120) {
            throw new IllegalArgumentException("Product name must contain 1 to 120 characters");
        }
        Objects.requireNonNull(measurementType, "measurementType");
        Objects.requireNonNull(pricingType, "pricingType");
        if (measurementType == MeasurementType.WEIGHT) {
            if (pricingType != PricingType.REGULAR || quantity != 1 || weightGrams == null || bundleQuantity != null) {
                throw new IllegalArgumentException("Weighted items require regular price, weight, and quantity 1");
            }
            ItemSubtotal.weight(referencePrice, weightGrams);
        } else {
            if (weightGrams != null) {
                throw new IllegalArgumentException("Unit items cannot carry weight");
            }
            if (pricingType == PricingType.BUNDLE) {
                if (bundleQuantity == null) {
                    throw new IllegalArgumentException("Bundle size is required");
                }
                ItemSubtotal.bundle(referencePrice, bundleQuantity, quantity);
            } else {
                if (bundleQuantity != null) {
                    throw new IllegalArgumentException("Regular items cannot carry a bundle size");
                }
                ItemSubtotal.unit(referencePrice, quantity);
            }
        }
    }

    public Money subtotal() {
        if (measurementType == MeasurementType.WEIGHT) {
            return ItemSubtotal.weight(referencePrice, weightGrams);
        }
        if (pricingType == PricingType.BUNDLE) {
            return ItemSubtotal.bundle(referencePrice, bundleQuantity, quantity);
        }
        return ItemSubtotal.unit(referencePrice, quantity);
    }
}

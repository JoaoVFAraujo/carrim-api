package br.com.carrim.domain.shopping;

import br.com.carrim.domain.shared.Money;
import java.util.Objects;

/** Pricing rules shared by the future shopping use cases, independently of HTTP and persistence. */
public final class ItemSubtotal {

    private ItemSubtotal() {}

    public static Money unit(Money unitPrice, int quantity) {
        validatePrice(unitPrice);
        validateQuantity(quantity);
        return unitPrice.times(quantity);
    }

    public static Money weight(Money pricePerKg, int grams) {
        validatePrice(pricePerKg);
        if (grams < 1 || grams > 9_999_999) {
            throw new IllegalArgumentException("Weight must be between 1 and 9999999 grams");
        }
        // Bounds keep the intermediate product within long. Round half a cent up, per line.
        long weightedCents = Math.multiplyExact(pricePerKg.cents(), grams);
        return new Money(Math.addExact(weightedCents, 500) / 1000);
    }

    public static Money bundle(Money bundlePrice, int bundleQuantity, int quantity) {
        validatePrice(bundlePrice);
        validateQuantity(quantity);
        if (bundleQuantity < 2 || bundleQuantity > 9999 || quantity % bundleQuantity != 0) {
            throw new IllegalArgumentException("Quantity must contain complete bundles of 2 to 9999 units");
        }
        // Multiply by whole groups, never by a rounded equivalent unit price.
        return bundlePrice.times(quantity / bundleQuantity);
    }

    private static void validatePrice(Money price) {
        Objects.requireNonNull(price, "price");
        if (price.cents() < 1 || price.cents() > 100_000_000) {
            throw new IllegalArgumentException("Price must be between 1 and 100000000 cents");
        }
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 9999) {
            throw new IllegalArgumentException("Quantity must be between 1 and 9999");
        }
    }
}

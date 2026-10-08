package br.com.carrim.adapter.in.web;

import br.com.carrim.application.catalog.CatalogRepository;
import br.com.carrim.application.shopping.*;
import br.com.carrim.domain.shared.Money;
import br.com.carrim.domain.shopping.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shopping-sessions")
public class ShoppingController {
    private final ShoppingRepository repository;
    private final ShoppingOperations operations;
    private final CatalogRepository catalog;
    private final CompletionRepository completion;

    public ShoppingController(
            ShoppingRepository repository,
            ShoppingOperations operations,
            CatalogRepository catalog,
            CompletionRepository completion) {
        this.repository = repository;
        this.operations = operations;
        this.catalog = catalog;
        this.completion = completion;
    }

    public record StartInput(
            @NotNull UUID id,
            @NotNull UUID supermarketId,
            @Positive @Max(100000000) Long budgetCents,
            @NotNull Instant startedAt) {}

    public record BudgetInput(
            @NotNull @Min(0) Long version,
            @Positive @Max(100000000) Long budgetCents) {}

    public record CompleteInput(
            @NotNull @Min(0) Long version,
            @NotNull Instant completedAt,
            @Min(0) @Max(9007199254740991L) Long checkoutTotalCents) {}

    public record CancelInput(
            @NotNull @Min(0) Long version, @NotNull Instant canceledAt) {}

    public record ItemInput(
            @NotNull UUID id,
            UUID productId,
            @NotBlank @Size(max = 120) String productNameSnapshot,
            @NotNull MeasurementType measurementType,
            @NotNull PricingType pricingType,
            Integer quantityUnits,
            Long unitPriceCents,
            Long pricePerKgCents,
            Integer weightGrams,
            Integer bundleQuantity,
            Long bundlePriceCents) {
        ShoppingItem toDomain(UUID session) {
            long price;
            int quantity;
            if (measurementType == MeasurementType.WEIGHT) {
                if (pricingType != PricingType.REGULAR
                        || quantityUnits != null
                        || unitPriceCents != null
                        || bundlePriceCents != null
                        || bundleQuantity != null
                        || pricePerKgCents == null) throw new IllegalArgumentException("Invalid weighted fields");
                price = pricePerKgCents;
                quantity = 1;
            } else {
                if (quantityUnits == null || pricePerKgCents != null || weightGrams != null)
                    throw new IllegalArgumentException("Invalid unit fields");
                quantity = quantityUnits;
                if (pricingType == PricingType.BUNDLE) {
                    if (bundlePriceCents == null || unitPriceCents != null)
                        throw new IllegalArgumentException("Invalid bundle fields");
                    price = bundlePriceCents;
                } else {
                    if (unitPriceCents == null || bundleQuantity != null || bundlePriceCents != null)
                        throw new IllegalArgumentException("Invalid regular fields");
                    price = unitPriceCents;
                }
            }
            return new ShoppingItem(
                    id,
                    session,
                    productId,
                    productNameSnapshot,
                    measurementType,
                    pricingType,
                    new Money(price),
                    quantity,
                    weightGrams,
                    bundleQuantity);
        }
    }

    public record ItemWrite(
            @NotNull @Min(0) Long version, @NotNull @Valid ItemInput item) {}

    public record ItemView(
            UUID id,
            UUID productId,
            String productNameSnapshot,
            MeasurementType measurementType,
            PricingType pricingType,
            Integer quantityUnits,
            Long unitPriceCents,
            Long pricePerKgCents,
            Integer weightGrams,
            Integer bundleQuantity,
            Long bundlePriceCents,
            long subtotalCents) {
        static ItemView of(ShoppingItem item) {
            return new ItemView(
                    item.id(),
                    item.productId(),
                    item.productNameSnapshot(),
                    item.measurementType(),
                    item.pricingType(),
                    item.measurementType() == MeasurementType.UNIT ? item.quantity() : null,
                    item.measurementType() == MeasurementType.UNIT && item.pricingType() == PricingType.REGULAR
                            ? item.referencePrice().cents()
                            : null,
                    item.measurementType() == MeasurementType.WEIGHT
                            ? item.referencePrice().cents()
                            : null,
                    item.weightGrams(),
                    item.bundleQuantity(),
                    item.pricingType() == PricingType.BUNDLE
                            ? item.referencePrice().cents()
                            : null,
                    item.subtotal().cents());
        }
    }

    public record ShoppingView(
            UUID id,
            UUID supermarketId,
            Long budgetCents,
            ShoppingStatus status,
            Instant startedAt,
            Instant finishedAt,
            Long checkoutTotalCents,
            Long checkoutDifferenceCents,
            long calculatedTotalCents,
            long version,
            List<ItemView> items) {
        static ShoppingView of(StoredShopping stored) {
            var s = stored.value();
            return new ShoppingView(
                    s.id(),
                    s.supermarketId(),
                    cents(s.budget()),
                    s.status(),
                    s.startedAt(),
                    s.finishedAt(),
                    cents(s.checkoutTotal()),
                    cents(s.checkoutDifference()),
                    s.total().cents(),
                    stored.version(),
                    s.items().stream().map(ItemView::of).toList());
        }
    }

    public record HistoryView(
            UUID id,
            UUID supermarketId,
            Instant observedAt,
            String comparisonBasis,
            long normalizedPriceCents,
            boolean approximate,
            ItemView item) {
        static HistoryView of(PriceHistoryEntry p) {
            var i = p.item();
            return new HistoryView(
                    p.id(),
                    p.supermarketId(),
                    p.observedAt(),
                    p.comparisonBasis().name(),
                    p.normalizedPrice().cents(),
                    i.pricingType() == PricingType.BUNDLE && i.referencePrice().cents() % i.bundleQuantity() != 0,
                    ItemView.of(i));
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingView start(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @Valid @RequestBody StartInput input) {
        UUID owner = ApiActor.owner(actor);
        catalog.findSupermarket(owner, input.supermarketId()).orElseThrow(NoSuchElementException::new);
        return ShoppingView.of(operations.start(
                owner,
                input.id(),
                input.supermarketId(),
                money(input.budgetCents()),
                input.startedAt().truncatedTo(ChronoUnit.MICROS)));
    }

    @GetMapping
    public List<ShoppingSummary> list(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @RequestParam(required = false) ShoppingStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return repository.list(ApiActor.owner(actor), status, limit, offset);
    }

    @GetMapping("/{id}")
    public ShoppingView get(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable UUID id) {
        return ShoppingView.of(owned(ApiActor.owner(actor), id));
    }

    @GetMapping("/active")
    public ResponseEntity<ShoppingView> active(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor) {
        return repository
                .active(ApiActor.owner(actor))
                .map(value -> ResponseEntity.ok(ShoppingView.of(value)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping("/{id}")
    public ShoppingView budget(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody BudgetInput input) {
        return ShoppingView.of(
                operations.changeBudget(ApiActor.owner(actor), id, input.version(), money(input.budgetCents())));
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingView add(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ItemWrite input) {
        UUID owner = ApiActor.owner(actor);
        var current = owned(owner, id);
        if (current.value().items().size() >= 1000) throw new IllegalArgumentException("Item limit reached");
        product(owner, input.item().productId());
        return ShoppingView.of(
                operations.addItem(owner, id, input.version(), input.item().toDomain(id)));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ShoppingView replace(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemWrite input) {
        if (!itemId.equals(input.item().id())) throw new IllegalArgumentException("Item IDs must match");
        UUID owner = ApiActor.owner(actor);
        product(owner, input.item().productId());
        return ShoppingView.of(
                operations.replaceItem(owner, id, input.version(), input.item().toDomain(id)));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ShoppingView remove(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestParam long version) {
        if (version < 0) throw new IllegalArgumentException("Invalid version");
        return ShoppingView.of(operations.removeItem(ApiActor.owner(actor), id, version, itemId));
    }

    @PostMapping("/{id}/complete")
    public ShoppingView complete(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody CompleteInput input) {
        return ShoppingView.of(completion.complete(
                ApiActor.owner(actor),
                id,
                input.version(),
                input.completedAt(),
                money(input.checkoutTotalCents()),
                key));
    }

    @PostMapping("/{id}/cancel")
    public ShoppingView cancel(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody CancelInput input) {
        return ShoppingView.of(operations.cancel(
                ApiActor.owner(actor), id, input.version(), input.canceledAt().truncatedTo(ChronoUnit.MICROS)));
    }

    @GetMapping("/{id}/prices")
    public List<HistoryView> prices(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable UUID id) {
        UUID owner = ApiActor.owner(actor);
        owned(owner, id);
        return repository.history(owner, id).stream().map(HistoryView::of).toList();
    }

    private StoredShopping owned(UUID owner, UUID id) {
        return repository.find(owner, id).orElseThrow(NoSuchElementException::new);
    }

    private void product(UUID owner, UUID id) {
        if (id != null) catalog.findProduct(owner, id).orElseThrow(NoSuchElementException::new);
    }

    private static Long cents(Money value) {
        return value == null ? null : value.cents();
    }

    private static Money money(Long value) {
        return value == null ? null : new Money(value);
    }
}

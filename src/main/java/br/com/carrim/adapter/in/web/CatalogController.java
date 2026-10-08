package br.com.carrim.adapter.in.web;

import br.com.carrim.application.catalog.*;
import br.com.carrim.application.shopping.ShoppingRepository;
import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.domain.supermarket.Supermarket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogRepository catalog;
    private final ShoppingRepository shopping;

    public CatalogController(CatalogRepository catalog, ShoppingRepository shopping) {
        this.catalog = catalog;
        this.shopping = shopping;
    }

    public record ProductInput(
            @NotNull UUID id,
            @NotBlank @Size(max = 120) String name,
            String barcode,
            @NotNull MeasurementType measurementType) {}

    public record ProductUpdate(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 120) String name,
            String barcode,
            @NotNull MeasurementType measurementType) {}

    public record MarketInput(
            @NotNull UUID id, @NotBlank @Size(max = 120) String name) {}

    public record MarketUpdate(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 120) String name) {}

    public record ProductView(UUID id, String name, String barcode, MeasurementType measurementType, long version) {
        static ProductView of(Versioned<Product> record) {
            var p = record.value();
            return new ProductView(p.id(), p.name(), p.barcode(), p.measurementType(), record.version());
        }
    }

    public record MarketView(UUID id, String name, long version) {
        static MarketView of(Versioned<Supermarket> record) {
            return new MarketView(record.value().id(), record.value().name(), record.version());
        }
    }

    public record LastPrice(long priceCents, java.time.Instant recordedAt) {}

    public record BarcodeView(ProductView product, LastPrice lastPrice) {}

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @Valid @RequestBody ProductInput input) {
        return ProductView.of(catalog.createProduct(
                ApiActor.owner(actor),
                new Product(input.id(), input.name(), input.barcode(), input.measurementType())));
    }

    @GetMapping("/products")
    public List<ProductView> products(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        ApiInputs.page(limit, offset);
        return catalog.products(ApiActor.owner(actor), limit, offset).stream()
                .map(ProductView::of)
                .toList();
    }

    @GetMapping("/products/{id}")
    public ProductView product(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable UUID id) {
        return ProductView.of(catalog.findProduct(ApiActor.owner(actor), id).orElseThrow(NoSuchElementException::new));
    }

    @PutMapping("/products/{id}")
    public ProductView update(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpdate input) {
        return ProductView.of(catalog.updateProduct(
                ApiActor.owner(actor),
                new Product(id, input.name(), input.barcode(), input.measurementType()),
                input.version()));
    }

    @GetMapping("/products/by-barcode/{code}")
    public BarcodeView barcode(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable String code,
            @RequestParam(required = false) UUID supermarketId) {
        ApiInputs.barcode(code);
        UUID owner = ApiActor.owner(actor);
        var product = catalog.productByBarcode(owner, code).orElseThrow(NoSuchElementException::new);
        LastPrice last = null;
        if (supermarketId != null) {
            catalog.findSupermarket(owner, supermarketId).orElseThrow(NoSuchElementException::new);
            last = shopping.lastPrice(
                            owner,
                            product.value().id(),
                            supermarketId,
                            product.value().measurementType())
                    .map(price -> new LastPrice(price.item().referencePrice().cents(), price.observedAt()))
                    .orElse(null);
        }
        return new BarcodeView(ProductView.of(product), last);
    }

    @PostMapping("/supermarkets")
    @ResponseStatus(HttpStatus.CREATED)
    public MarketView createMarket(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @Valid @RequestBody MarketInput input) {
        return MarketView.of(
                catalog.createSupermarket(ApiActor.owner(actor), new Supermarket(input.id(), input.name())));
    }

    @GetMapping("/supermarkets")
    public List<MarketView> markets(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        ApiInputs.page(limit, offset);
        return catalog.supermarkets(ApiActor.owner(actor), limit, offset).stream()
                .map(MarketView::of)
                .toList();
    }

    @GetMapping("/supermarkets/{id}")
    public MarketView market(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable UUID id) {
        return MarketView.of(
                catalog.findSupermarket(ApiActor.owner(actor), id).orElseThrow(NoSuchElementException::new));
    }

    @PutMapping("/supermarkets/{id}")
    public MarketView updateMarket(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody MarketUpdate input) {
        return MarketView.of(
                catalog.updateSupermarket(ApiActor.owner(actor), new Supermarket(id, input.name()), input.version()));
    }
}

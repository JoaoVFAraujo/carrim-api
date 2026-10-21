package br.com.carrim.adapter.in.web;

import br.com.carrim.application.catalog.CatalogLookup;
import br.com.carrim.application.catalog.ProductLookup;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductLookupController {
    private final CatalogLookup lookup;

    public ProductLookupController(CatalogLookup lookup) {
        this.lookup = lookup;
    }

    @GetMapping("/api/v1/products/lookup/{code}")
    public ResponseEntity<ProductLookup> lookup(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable String code) {
        ApiInputs.barcode(code);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(lookup.lookup(ApiActor.owner(actor), code));
    }
}

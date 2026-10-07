package br.com.carrim.application.catalog;

import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.supermarket.Supermarket;
import java.util.Optional;
import java.util.UUID;

public interface CatalogRepository {
    Versioned<Product> createProduct(UUID ownerId, Product product);

    Optional<Versioned<Product>> findProduct(UUID ownerId, UUID id);

    Versioned<Product> updateProduct(UUID ownerId, Product product, long expectedVersion);

    Versioned<Supermarket> createSupermarket(UUID ownerId, Supermarket supermarket);

    Optional<Versioned<Supermarket>> findSupermarket(UUID ownerId, UUID id);

    Versioned<Supermarket> updateSupermarket(UUID ownerId, Supermarket supermarket, long expectedVersion);
}

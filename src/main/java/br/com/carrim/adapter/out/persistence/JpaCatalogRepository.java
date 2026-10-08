package br.com.carrim.adapter.out.persistence;

import br.com.carrim.application.catalog.CatalogConflictException;
import br.com.carrim.application.catalog.CatalogRepository;
import br.com.carrim.application.catalog.Versioned;
import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.supermarket.Supermarket;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaCatalogRepository implements CatalogRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<Versioned<Product>> products(UUID owner, int limit, int offset) {
        page(limit, offset);
        return entityManager
                .createQuery(
                        "select p from ProductEntity p where p.ownerId=:owner order by p.name,p.id",
                        ProductEntity.class)
                .setParameter("owner", Objects.requireNonNull(owner))
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultStream()
                .map(row -> new Versioned<>(row.toDomain(), row.version))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Versioned<Product>> productByBarcode(UUID owner, String code) {
        if (code == null || !code.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13})"))
            throw new IllegalArgumentException("Invalid barcode");
        return entityManager
                .createQuery(
                        "select p from ProductEntity p where p.ownerId=:owner and p.barcode=:code", ProductEntity.class)
                .setParameter("owner", Objects.requireNonNull(owner))
                .setParameter("code", code)
                .getResultStream()
                .map(row -> new Versioned<>(row.toDomain(), row.version))
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Versioned<Supermarket>> supermarkets(UUID owner, int limit, int offset) {
        page(limit, offset);
        return entityManager
                .createQuery(
                        "select s from SupermarketEntity s where s.ownerId=:owner order by s.name,s.id",
                        SupermarketEntity.class)
                .setParameter("owner", Objects.requireNonNull(owner))
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultStream()
                .map(row -> new Versioned<>(row.toDomain(), row.version))
                .toList();
    }

    private static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 1000000)
            throw new IllegalArgumentException("Invalid page");
    }

    @Override
    public Versioned<Product> createProduct(UUID ownerId, Product product) {
        var row = new ProductEntity(Objects.requireNonNull(ownerId), Objects.requireNonNull(product));
        entityManager.persist(row);
        entityManager.flush();
        return new Versioned<>(row.toDomain(), row.version);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Versioned<Product>> findProduct(UUID ownerId, UUID id) {
        return productRow(ownerId, id).map(row -> new Versioned<>(row.toDomain(), row.version));
    }

    @Override
    public Versioned<Product> updateProduct(UUID ownerId, Product product, long expectedVersion) {
        Objects.requireNonNull(product);
        var row = productRow(ownerId, product.id()).orElseThrow(NoSuchElementException::new);
        verifyVersion(row.version, expectedVersion);
        row.apply(product);
        flushUpdate();
        return new Versioned<>(row.toDomain(), row.version);
    }

    @Override
    public Versioned<Supermarket> createSupermarket(UUID ownerId, Supermarket supermarket) {
        var row = new SupermarketEntity(Objects.requireNonNull(ownerId), Objects.requireNonNull(supermarket));
        entityManager.persist(row);
        entityManager.flush();
        return new Versioned<>(row.toDomain(), row.version);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Versioned<Supermarket>> findSupermarket(UUID ownerId, UUID id) {
        return supermarketRow(ownerId, id).map(row -> new Versioned<>(row.toDomain(), row.version));
    }

    @Override
    public Versioned<Supermarket> updateSupermarket(UUID ownerId, Supermarket supermarket, long expectedVersion) {
        Objects.requireNonNull(supermarket);
        var row = supermarketRow(ownerId, supermarket.id()).orElseThrow(NoSuchElementException::new);
        verifyVersion(row.version, expectedVersion);
        row.name = supermarket.name();
        flushUpdate();
        return new Versioned<>(row.toDomain(), row.version);
    }

    private Optional<ProductEntity> productRow(UUID ownerId, UUID id) {
        return entityManager
                .createQuery(
                        "select p from ProductEntity p where p.ownerId = :owner and p.id = :id", ProductEntity.class)
                .setParameter("owner", Objects.requireNonNull(ownerId))
                .setParameter("id", Objects.requireNonNull(id))
                .getResultStream()
                .findFirst();
    }

    private Optional<SupermarketEntity> supermarketRow(UUID ownerId, UUID id) {
        return entityManager
                .createQuery(
                        "select s from SupermarketEntity s where s.ownerId = :owner and s.id = :id",
                        SupermarketEntity.class)
                .setParameter("owner", Objects.requireNonNull(ownerId))
                .setParameter("id", Objects.requireNonNull(id))
                .getResultStream()
                .findFirst();
    }

    private void verifyVersion(long actual, long expected) {
        if (actual != expected) {
            throw new CatalogConflictException();
        }
    }

    private void flushUpdate() {
        try {
            entityManager.flush();
        } catch (OptimisticLockException exception) {
            throw new CatalogConflictException();
        }
    }
}

package br.com.carrim.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.carrim.application.catalog.CatalogConflictException;
import br.com.carrim.application.catalog.CatalogRepository;
import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.domain.supermarket.Supermarket;
import br.com.carrim.support.PostgresTestSupport;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CatalogPersistenceTests extends PostgresTestSupport {
    @Autowired
    private CatalogRepository catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    private UUID owner() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO carrim.users(id, account_type) VALUES (?, 'ANONYMOUS')", id);
        return id;
    }

    private Product product(String code) {
        return new Product(UUID.randomUUID(), "Café", code, MeasurementType.UNIT);
    }

    @Test
    void migrationReplayPreservesPersistedCatalogData() {
        UUID owner = owner();
        Product product = product("12345678");
        catalog.createProduct(owner, product);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(
                product, catalog.findProduct(owner, product.id()).orElseThrow().value());
    }

    @Test
    void roundTripsProductsWithLeadingZerosAndOwnerIsolation() {
        UUID owner = owner();
        Product product = product("0789600112233");
        var created = catalog.createProduct(owner, product);
        assertEquals(0, created.version());
        assertEquals(
                product, catalog.findProduct(owner, product.id()).orElseThrow().value());
        assertTrue(catalog.findProduct(owner(), product.id()).isEmpty());
    }

    @Test
    void updatesWithVersionAndRejectsStaleOrForeignWrites() {
        UUID owner = owner();
        Product product = product("12345678");
        catalog.createProduct(owner, product);
        var updated = catalog.updateProduct(owner, product.rename("Novo nome"), 0);
        assertEquals(1, updated.version());
        assertThrows(CatalogConflictException.class, () -> catalog.updateProduct(owner, product.rename("Stale"), 0));
        assertThrows(NoSuchElementException.class, () -> catalog.updateProduct(owner(), product.rename("Foreign"), 1));
        assertEquals(
                "Novo nome",
                catalog.findProduct(owner, product.id()).orElseThrow().value().name());
    }

    @Test
    void scopesBarcodeUniquenessByOwnerAndAllowsMultipleManualProducts() {
        UUID owner = owner();
        catalog.createProduct(owner, product("12345678"));
        assertThrows(DataIntegrityViolationException.class, () -> catalog.createProduct(owner, product("12345678")));
        catalog.createProduct(owner(), product("12345678"));
        catalog.createProduct(owner, product(null));
        catalog.createProduct(owner, product(null));
    }

    @Test
    void refusesMissingOwnersAndDoesNotOverwriteExistingClientIds() {
        Product product = product("12345678");
        assertThrows(DataIntegrityViolationException.class, () -> catalog.createProduct(UUID.randomUUID(), product));
        UUID owner = owner();
        catalog.createProduct(owner, product);
        assertThrows(
                DataIntegrityViolationException.class, () -> catalog.createProduct(owner(), product.rename("Other")));
        assertEquals(
                "Café",
                catalog.findProduct(owner, product.id()).orElseThrow().value().name());
    }

    @Test
    void roundTripsSupermarketsWithIndependentBranchesAndOwnerIsolation() {
        UUID owner = owner();
        Supermarket market = new Supermarket(UUID.randomUUID(), "São Luiz");
        assertEquals(0, catalog.createSupermarket(owner, market).version());
        catalog.createSupermarket(owner, new Supermarket(UUID.randomUUID(), market.name()));
        var updated = catalog.updateSupermarket(owner, market.rename("Filial Centro"), 0);
        assertEquals(1, updated.version());
        assertTrue(catalog.findSupermarket(owner(), market.id()).isEmpty());
        assertThrows(CatalogConflictException.class, () -> catalog.updateSupermarket(owner, market, 0));
        assertThrows(NoSuchElementException.class, () -> catalog.updateSupermarket(owner(), market, 1));
        assertEquals(
                "Filial Centro",
                catalog.findSupermarket(owner, market.id())
                        .orElseThrow()
                        .value()
                        .name());
    }

    @Test
    void databaseRejectsInvalidCodesMeasurementNamesAndVersions() {
        UUID owner = owner();
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "INSERT INTO carrim.supermarkets(id,user_id,name) VALUES (?,?,?)",
                        UUID.randomUUID(),
                        owner,
                        "\t\n"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "INSERT INTO carrim.products(id,user_id,name,barcode,measurement_type) VALUES (?,?,?,?,'UNIT')",
                        UUID.randomUUID(),
                        owner,
                        "Coffee",
                        "invalid"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "INSERT INTO carrim.products(id,user_id,name,measurement_type) VALUES (?,?,'Coffee','UNKNOWN')",
                        UUID.randomUUID(),
                        owner));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "INSERT INTO carrim.supermarkets(id,user_id,name) VALUES (?,?,'   ')",
                        UUID.randomUUID(),
                        owner));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "INSERT INTO carrim.supermarkets(id,user_id,name,version) VALUES (?,?,'Market',-1)",
                        UUID.randomUUID(),
                        owner));
    }

    @Test
    void concurrentUpdatesCannotSilentlyOverwriteEachOther() throws Exception {
        UUID owner = owner();
        Product product = product("12345678");
        catalog.createProduct(owner, product);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> updateAfter(gate, owner, product.rename("A")), executor);
            var second = CompletableFuture.supplyAsync(() -> updateAfter(gate, owner, product.rename("B")), executor);
            gate.countDown();
            assertEquals(1, first.get() + second.get());
        }
        assertEquals(1, catalog.findProduct(owner, product.id()).orElseThrow().version());
    }

    private int updateAfter(CountDownLatch gate, UUID owner, Product product) {
        try {
            gate.await();
            catalog.updateProduct(owner, product, 0);
            return 1;
        } catch (CatalogConflictException exception) {
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

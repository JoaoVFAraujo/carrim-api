package br.com.carrim.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.com.carrim.application.catalog.CatalogRepository;
import br.com.carrim.application.shopping.ShoppingConflictException;
import br.com.carrim.application.shopping.ShoppingOperations;
import br.com.carrim.application.shopping.ShoppingRepository;
import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shared.Money;
import br.com.carrim.domain.shopping.*;
import br.com.carrim.domain.supermarket.Supermarket;
import br.com.carrim.support.PostgresTestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ShoppingPersistenceTests extends PostgresTestSupport {
    @Autowired
    ShoppingRepository repository;

    @Autowired
    ShoppingOperations operations;

    @Autowired
    CatalogRepository catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    private static final Instant START = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant FINISH = START.plusSeconds(60);

    private record Fixture(UUID owner, UUID market, UUID id) {}

    private Fixture fixture() {
        UUID owner = UUID.randomUUID();
        jdbc.update("INSERT INTO carrim.users(id,account_type) VALUES (?,'ANONYMOUS')", owner);
        UUID market = UUID.randomUUID();
        catalog.createSupermarket(owner, new Supermarket(market, "Market"));
        UUID id = UUID.randomUUID();
        operations.start(owner, id, market, null, START);
        return new Fixture(owner, market, id);
    }

    private ShoppingItem unit(Fixture f, UUID id, int quantity, UUID product) {
        return new ShoppingItem(
                id,
                f.id(),
                product,
                "Coffee",
                MeasurementType.UNIT,
                PricingType.REGULAR,
                new Money(799),
                quantity,
                null,
                null);
    }

    private long count(String table, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM carrim." + table + " WHERE session_id=?", Long.class, id);
    }

    @Test
    void predictedNextVersionCannotExceedLimitAfterWaitingForTheAggregateLock() throws Exception {
        Fixture f = fixture();
        var initial = new ArrayList<ShoppingItem>();
        for (int index = 0; index < ShoppingSession.MAX_ITEMS - 1; index++) {
            initial.add(unit(f, UUID.randomUUID(), 1, null));
        }
        repository.mutate(
                f.owner(),
                f.id(),
                0,
                current -> new ShoppingSession(
                        current.id(),
                        current.supermarketId(),
                        current.budget(),
                        current.status(),
                        current.startedAt(),
                        current.finishedAt(),
                        current.checkoutTotal(),
                        initial));
        var started = new CountDownLatch(1);
        var pending = new AtomicReference<CompletableFuture<Boolean>>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                operations.addItem(f.owner(), f.id(), 1, unit(f, UUID.randomUUID(), 1, null));
                pending.set(CompletableFuture.supplyAsync(
                        () -> {
                            started.countDown();
                            try {
                                operations.addItem(f.owner(), f.id(), 2, unit(f, UUID.randomUUID(), 1, null));
                                return true;
                            } catch (IllegalArgumentException expected) {
                                return false;
                            }
                        },
                        executor));
                try {
                    assertTrue(started.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            });
            assertFalse(pending.get().get(10, TimeUnit.SECONDS));
        }
        var persisted = repository.find(f.owner(), f.id()).orElseThrow();
        assertEquals(ShoppingSession.MAX_ITEMS, persisted.value().items().size());
        assertEquals(2, persisted.version());
    }

    @Test
    void persistsMixedBasketAndFinalizesWithImmutableExactHistory() {
        Fixture f = fixture();
        var first = unit(f, UUID.randomUUID(), 2, null);
        operations.addItem(f.owner(), f.id(), 0, first);
        var weight = new ShoppingItem(
                UUID.randomUUID(),
                f.id(),
                null,
                "Banana",
                MeasurementType.WEIGHT,
                PricingType.REGULAR,
                new Money(699),
                1,
                824,
                null);
        operations.addItem(f.owner(), f.id(), 1, weight);
        var bundle = new ShoppingItem(
                UUID.randomUUID(),
                f.id(),
                null,
                "Milk",
                MeasurementType.UNIT,
                PricingType.BUNDLE,
                new Money(1000),
                6,
                null,
                3);
        operations.addItem(f.owner(), f.id(), 2, bundle);
        operations.changeBudget(f.owner(), f.id(), 3, new Money(4000));
        var loaded = repository.find(f.owner(), f.id()).orElseThrow();
        assertEquals(new Money(4174), loaded.value().total());
        assertEquals(new Money(-174), loaded.value().remainingBudget());
        assertEquals(
                4174L,
                jdbc.queryForObject(
                        "SELECT sum(subtotal_cents) FROM carrim.shopping_items WHERE session_id=?",
                        Long.class,
                        f.id()));
        var completed = operations.complete(f.owner(), f.id(), 4, FINISH, new Money(4200));
        assertEquals(5, completed.version());
        assertEquals(new Money(26), completed.value().checkoutDifference());
        var history = repository.history(f.owner(), f.id());
        assertEquals(3, history.size());
        assertEquals(new Money(333), history.get(2).normalizedPrice());
        assertEquals(new Money(1000), history.get(2).item().referencePrice());
        assertEquals(FINISH, history.get(0).observedAt());
        assertThrows(IllegalStateException.class, () -> operations.removeItem(f.owner(), f.id(), 5, first.id()));
        assertThrows(ShoppingConflictException.class, () -> operations.complete(f.owner(), f.id(), 4, FINISH, null));
        assertEquals(3, count("price_observations", f.id()));
    }

    @Test
    void editsRemovesAndKeepsVersionsAndOrder() {
        Fixture f = fixture();
        UUID line = UUID.randomUUID();
        operations.addItem(f.owner(), f.id(), 0, unit(f, line, 1, null));
        operations.replaceItem(f.owner(), f.id(), 1, unit(f, line, 2, null));
        assertEquals(
                new Money(1598),
                repository.find(f.owner(), f.id()).orElseThrow().value().total());
        operations.removeItem(f.owner(), f.id(), 2, line);
        assertTrue(
                repository.find(f.owner(), f.id()).orElseThrow().value().items().isEmpty());
        assertEquals(3, repository.find(f.owner(), f.id()).orElseThrow().version());
        assertThrows(
                ShoppingConflictException.class, () -> operations.changeBudget(f.owner(), f.id(), 0, new Money(1)));
    }

    @Test
    void isolatesOwnersProductsAndMarkets() {
        Fixture first = fixture();
        Fixture second = fixture();
        assertTrue(repository.find(second.owner(), first.id()).isEmpty());
        assertTrue(repository.history(second.owner(), first.id()).isEmpty());
        assertThrows(NoSuchElementException.class, () -> operations.cancel(second.owner(), first.id(), 0, FINISH));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> operations.start(UUID.randomUUID(), UUID.randomUUID(), first.market(), null, START));
        operations.cancel(first.owner(), first.id(), 0, FINISH);
        assertThrows(
                DataIntegrityViolationException.class,
                () -> operations.start(first.owner(), UUID.randomUUID(), second.market(), null, START));
        Product foreign = new Product(UUID.randomUUID(), "Other", null, MeasurementType.UNIT);
        catalog.createProduct(second.owner(), foreign);
        UUID next = UUID.randomUUID();
        operations.start(first.owner(), next, first.market(), null, START);
        ShoppingItem line = new ShoppingItem(
                UUID.randomUUID(),
                next,
                foreign.id(),
                "Foreign",
                MeasurementType.UNIT,
                PricingType.REGULAR,
                new Money(1),
                1,
                null,
                null);
        assertThrows(DataIntegrityViolationException.class, () -> operations.addItem(first.owner(), next, 0, line));
        assertTrue(repository
                .find(first.owner(), next)
                .orElseThrow()
                .value()
                .items()
                .isEmpty());
    }

    @Test
    void enforcesOneActiveSessionAndCancelDoesNotProducePrices() {
        Fixture f = fixture();
        assertThrows(
                DataIntegrityViolationException.class,
                () -> operations.start(f.owner(), UUID.randomUUID(), f.market(), null, START));
        operations.cancel(f.owner(), f.id(), 0, FINISH);
        assertEquals(0, count("price_observations", f.id()));
        operations.start(f.owner(), UUID.randomUUID(), f.market(), null, START);
        assertThrows(IllegalStateException.class, () -> operations.changeBudget(f.owner(), f.id(), 1, null));
    }

    @Test
    void failureDuringHistoryInsertionRollsBackStatusVersionAndEarlierObservations() {
        Fixture f = fixture();
        operations.addItem(f.owner(), f.id(), 0, unit(f, UUID.randomUUID(), 1, null));
        UUID failId = UUID.randomUUID();
        operations.addItem(f.owner(), f.id(), 1, unit(f, failId, 1, null));
        jdbc.execute(
                "CREATE FUNCTION carrim.test_fail_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"
                        + failId
                        + "'::uuid THEN RAISE EXCEPTION 'injected failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        jdbc.execute(
                "CREATE TRIGGER test_fail_history BEFORE INSERT ON carrim.price_observations FOR EACH ROW EXECUTE FUNCTION carrim.test_fail_history()");
        try {
            assertThrows(DataAccessException.class, () -> operations.complete(f.owner(), f.id(), 2, FINISH, null));
            var state = repository.find(f.owner(), f.id()).orElseThrow();
            assertEquals(ShoppingStatus.ACTIVE, state.value().status());
            assertEquals(2, state.version());
            assertEquals(0, count("price_observations", f.id()));
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_history ON carrim.price_observations");
            jdbc.execute("DROP FUNCTION carrim.test_fail_history()");
        }
        operations.complete(f.owner(), f.id(), 2, FINISH, null);
        assertEquals(2, count("price_observations", f.id()));
    }

    @Test
    void databaseProtectsTerminalHistoryAndRejectsPartialCompletion() {
        Fixture f = fixture();
        UUID line = UUID.randomUUID();
        operations.addItem(f.owner(), f.id(), 0, unit(f, line, 1, null));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE carrim.shopping_sessions SET status='COMPLETED',finished_at=?,version=version+1 WHERE id=?",
                        java.sql.Timestamp.from(FINISH),
                        f.id()));
        assertEquals(
                ShoppingStatus.ACTIVE,
                repository.find(f.owner(), f.id()).orElseThrow().value().status());
        operations.complete(f.owner(), f.id(), 1, FINISH, null);
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("UPDATE carrim.shopping_items SET reference_price_cents=1 WHERE id=?", line));
        assertThrows(
                DataAccessException.class, () -> jdbc.update("DELETE FROM carrim.price_observations WHERE id=?", line));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE carrim.shopping_sessions SET status='ACTIVE',finished_at=NULL,version=version+1 WHERE id=?",
                        f.id()));
        assertEquals(
                new Money(799), repository.history(f.owner(), f.id()).getFirst().normalizedPrice());
    }

    @Test
    void concurrentCompletionProducesHistoryOnlyOnce() throws Exception {
        Fixture f = fixture();
        operations.addItem(f.owner(), f.id(), 0, unit(f, UUID.randomUUID(), 1, null));
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> finishAfter(gate, f), executor);
            var second = CompletableFuture.supplyAsync(() -> finishAfter(gate, f), executor);
            gate.countDown();
            assertEquals(1, first.get() + second.get());
        }
        assertEquals(1, count("price_observations", f.id()));
        assertEquals(2, repository.find(f.owner(), f.id()).orElseThrow().version());
    }

    @Test
    void snapshotDoesNotChangeWhenCatalogIsRenamed() {
        Fixture f = fixture();
        Product product = new Product(UUID.randomUUID(), "Coffee", "12345678", MeasurementType.UNIT);
        catalog.createProduct(f.owner(), product);
        operations.addItem(f.owner(), f.id(), 0, unit(f, UUID.randomUUID(), 1, product.id()));
        operations.complete(f.owner(), f.id(), 1, FINISH, null);
        catalog.updateProduct(f.owner(), product.rename("Renamed"), 0);
        assertEquals(
                "Coffee",
                repository.history(f.owner(), f.id()).getFirst().item().productNameSnapshot());
    }

    @Test
    void acceptsRoundedZeroAndLargeCheckoutWithoutMixingReferences() {
        Fixture f = fixture();
        var tiny = new ShoppingItem(
                UUID.randomUUID(),
                f.id(),
                null,
                "Tiny",
                MeasurementType.UNIT,
                PricingType.BUNDLE,
                new Money(1),
                3,
                null,
                3);
        operations.addItem(f.owner(), f.id(), 0, tiny);
        operations.complete(f.owner(), f.id(), 1, FINISH, new Money(200000000));
        assertEquals(
                Money.ZERO, repository.history(f.owner(), f.id()).getFirst().normalizedPrice());
        assertEquals(
                new Money(199999999),
                repository.find(f.owner(), f.id()).orElseThrow().value().checkoutDifference());
    }

    private int finishAfter(CountDownLatch gate, Fixture f) {
        try {
            gate.await();
            operations.complete(f.owner(), f.id(), 1, FINISH, null);
            return 1;
        } catch (ShoppingConflictException exception) {
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

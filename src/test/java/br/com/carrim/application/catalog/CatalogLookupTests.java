package br.com.carrim.application.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shopping.MeasurementType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogLookupTests {
    static final String CODE = "7891000100103";

    static class MutableClock extends Clock {
        long millis = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli();

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    CatalogRepository repository() {
        var repository = mock(CatalogRepository.class);
        when(repository.productByBarcode(any(), any())).thenReturn(Optional.empty());
        return repository;
    }

    ExternalProductCatalog.Reply found(String code) {
        return new ExternalProductCatalog.Reply(
                new ProductLookup(
                        ProductLookup.Status.FOUND,
                        ProductLookup.Source.OPEN_FOOD_FACTS,
                        code,
                        "Coffee",
                        "Brand",
                        "500 g",
                        null,
                        "https://world.openfoodfacts.org/product/" + code,
                        "https://opendatacommons.org/licenses/odbl/1-0/",
                        null),
                0);
    }

    @Test
    void personalDataIsOwnerScopedAndNeverSharedOrOverwritten() {
        var repository = repository();
        UUID owner = UUID.randomUUID();
        when(repository.productByBarcode(owner, CODE))
                .thenReturn(Optional.of(new Versioned<>(
                        new Product(UUID.randomUUID(), "My correction", CODE, MeasurementType.WEIGHT), 0)));
        var provider = mock(ExternalProductCatalog.class);
        when(provider.lookup(CODE)).thenReturn(found(CODE));
        var lookup = new CatalogLookup(repository, provider, new MutableClock(), true);
        var own = lookup.lookup(owner, CODE);
        assertEquals("My correction", own.name());
        assertEquals(MeasurementType.WEIGHT, own.measurementType());
        assertEquals(ProductLookup.Source.PERSONAL, own.source());
        verifyNoInteractions(provider);
        assertEquals("Coffee", lookup.lookup(UUID.randomUUID(), CODE).name());
        verify(repository, never()).createProduct(any(), any());
    }

    @Test
    void cachesPositiveAndNegativeResultsWithDifferentExpiry() {
        var provider = mock(ExternalProductCatalog.class);
        String absent = "00000000";
        when(provider.lookup(CODE)).thenReturn(found(CODE));
        when(provider.lookup(absent))
                .thenReturn(new ExternalProductCatalog.Reply(
                        ProductLookup.empty(absent, ProductLookup.Status.NOT_FOUND), 0));
        var clock = new MutableClock();
        var lookup = new CatalogLookup(repository(), provider, clock, true);
        lookup.lookup(UUID.randomUUID(), CODE);
        lookup.lookup(UUID.randomUUID(), CODE);
        lookup.lookup(UUID.randomUUID(), absent);
        lookup.lookup(UUID.randomUUID(), absent);
        clock.millis += 16 * 60000;
        lookup.lookup(UUID.randomUUID(), absent);
        lookup.lookup(UUID.randomUUID(), CODE);
        verify(provider, times(1)).lookup(CODE);
        verify(provider, times(2)).lookup(absent);
        clock.millis += 25 * 3600000L;
        lookup.lookup(UUID.randomUUID(), CODE);
        verify(provider, times(2)).lookup(CODE);
    }

    @Test
    void failuresAreNotNegativeCacheAnd429RespectsCooldown() {
        var provider = mock(ExternalProductCatalog.class);
        when(provider.lookup(CODE))
                .thenReturn(
                        new ExternalProductCatalog.Reply(
                                ProductLookup.empty(CODE, ProductLookup.Status.UNAVAILABLE), 0),
                        found(CODE));
        var clock = new MutableClock();
        var lookup = new CatalogLookup(repository(), provider, clock, true);
        assertEquals(
                ProductLookup.Status.UNAVAILABLE,
                lookup.lookup(UUID.randomUUID(), CODE).status());
        assertEquals(
                ProductLookup.Status.FOUND,
                lookup.lookup(UUID.randomUUID(), CODE).status());
        String another = "00000000";
        when(provider.lookup(another))
                .thenReturn(new ExternalProductCatalog.Reply(
                        ProductLookup.empty(another, ProductLookup.Status.RATE_LIMITED), 120));
        lookup.lookup(UUID.randomUUID(), another);
        assertEquals(
                ProductLookup.Status.RATE_LIMITED,
                lookup.lookup(UUID.randomUUID(), "11111111").status());
        verify(provider, never()).lookup("11111111");
        clock.millis += 120001;
        when(provider.lookup("11111111")).thenReturn(found("11111111"));
        assertEquals(
                ProductLookup.Status.FOUND,
                lookup.lookup(UUID.randomUUID(), "11111111").status());
    }

    @Test
    void ownerAndRollingGlobalBudgetsAreBounded() {
        var clock = new MutableClock();
        var calls = new AtomicInteger();
        ExternalProductCatalog provider = code -> {
            calls.incrementAndGet();
            return found(code);
        };
        var lookup = new CatalogLookup(repository(), provider, clock, true);
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < 10; i++)
            assertEquals(ProductLookup.Status.FOUND, lookup.lookup(owner, CODE).status());
        assertEquals(
                ProductLookup.Status.RATE_LIMITED, lookup.lookup(owner, CODE).status());
        assertEquals(1, calls.get());
        for (int i = 0; i < 9; i++) lookup.lookup(UUID.randomUUID(), "1000000" + i);
        assertEquals(
                ProductLookup.Status.RATE_LIMITED,
                lookup.lookup(UUID.randomUUID(), "99999999").status());
        clock.millis += 60001;
        assertEquals(
                ProductLookup.Status.FOUND, lookup.lookup(owner, "99999999").status());
    }

    @Test
    void disabledStillAllowsOwnCatalogAndRejectsInvalidCodesBeforeAccess() {
        var repo = repository();
        var provider = mock(ExternalProductCatalog.class);
        var lookup = new CatalogLookup(repo, provider, new MutableClock(), false);
        assertEquals(
                ProductLookup.Status.DISABLED,
                lookup.lookup(UUID.randomUUID(), CODE).status());
        assertThrows(IllegalArgumentException.class, () -> lookup.lookup(UUID.randomUUID(), "https://localhost"));
        assertThrows(IllegalArgumentException.class, () -> lookup.lookup(UUID.randomUUID(), "１２３４５６７８"));
        verifyNoInteractions(provider);
    }

    @Test
    void duplicateRequestsShareOneFlightAndThirdDistinctRequestDoesNotWait() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        ExternalProductCatalog provider = code -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return found(code);
        };
        var lookup = new CatalogLookup(repository(), provider, new MutableClock(), true);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> lookup.lookup(UUID.randomUUID(), CODE));
            var second = executor.submit(() -> lookup.lookup(UUID.randomUUID(), "00000000"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var same = executor.submit(() -> lookup.lookup(UUID.randomUUID(), CODE));
            assertEquals(
                    ProductLookup.Status.UNAVAILABLE,
                    lookup.lookup(UUID.randomUUID(), "11111111").status());
            release.countDown();
            assertEquals(first.get(), same.get());
            assertEquals(ProductLookup.Status.FOUND, second.get().status());
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
        }
    }
}

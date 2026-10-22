package br.com.carrim.adapter.out.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.carrim.application.catalog.CatalogLookup;
import br.com.carrim.application.catalog.ProductLookup;
import br.com.carrim.application.photos.PhotoLimitException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoExternalImporterTests {
    private static final String CODE = "7898080643179";

    private CatalogLookup lookup() {
        var lookup = mock(CatalogLookup.class);
        when(lookup.externalPhoto(any(), eq(CODE)))
                .thenReturn(new ProductLookup(
                        ProductLookup.Status.FOUND,
                        ProductLookup.Source.OPEN_FOOD_FACTS,
                        CODE,
                        "Aveia",
                        null,
                        null,
                        "https://images.openfoodfacts.org/images/products/789/808/064/3179/front_pt.3.200.jpg",
                        "https://world.openfoodfacts.org/product/" + CODE,
                        "https://opendatacommons.org/licenses/odbl/1-0/",
                        null));
        return lookup;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> response() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", output);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(output.toByteArray());
        when(response.headers())
                .thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (k, v) -> true));
        return response;
    }

    @Test
    void imageDownloadsHaveTheirOwnRollingBudgetEvenWithCachedProductLookup() throws Exception {
        var http = mock(HttpClient.class);
        var reply = response();
        when(http.sendAsync(
                        any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(reply));
        var clock = new MutableClock();
        var importer = new PhotoExternalImporter(lookup(), new PhotoImageCodec(), http, clock);
        for (int count = 0; count < 10; count++)
            assertTrue(importer.fetch(UUID.randomUUID(), CODE).bytes().length > 0);
        assertThrows(PhotoLimitException.class, () -> importer.fetch(UUID.randomUUID(), CODE));
        verify(http, times(10))
                .sendAsync(
                        any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        clock.millis = 60001;
        assertTrue(importer.fetch(UUID.randomUUID(), CODE).bytes().length > 0);
        verify(http, times(11))
                .sendAsync(
                        any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    void concurrentDownloadsAreBoundedBeforeReceivingOrDecodingBodies() throws Exception {
        var http = mock(HttpClient.class);
        var started = new CountDownLatch(2);
        var exchange = new CompletableFuture<HttpResponse<byte[]>>();
        when(http.sendAsync(
                        any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(call -> {
                    started.countDown();
                    return exchange;
                });
        var importer = new PhotoExternalImporter(lookup(), new PhotoImageCodec(), http, new MutableClock());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> importer.fetch(UUID.randomUUID(), CODE));
            var second = pool.submit(() -> importer.fetch(UUID.randomUUID(), CODE));
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertThrows(PhotoLimitException.class, () -> importer.fetch(UUID.randomUUID(), CODE));
                verify(http, times(2))
                        .sendAsync(
                                any(HttpRequest.class),
                                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
            } finally {
                exchange.complete(response());
            }
            assertTrue(first.get(3, TimeUnit.SECONDS).bytes().length > 0);
            assertTrue(second.get(3, TimeUnit.SECONDS).bytes().length > 0);
        }
    }

    private static final class MutableClock extends Clock {
        long millis;

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId ignored) {
            return this;
        }

        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        public long millis() {
            return millis;
        }
    }
}

package br.com.carrim.adapter.out.catalog;

import br.com.carrim.application.catalog.CatalogLookup;
import br.com.carrim.application.catalog.ProductLookup;
import br.com.carrim.application.photos.PhotoLimitException;
import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Fixed-source import with a separate budget for image HTTP and decoding. */
public final class PhotoExternalImporter {
    public record Imported(byte[] bytes, String sourceUrl, String licenseUrl) {}

    private final CatalogLookup lookup;
    private final PhotoImageCodec codec;
    private final HttpClient http;
    private final Clock clock;
    private final Semaphore processing = new Semaphore(2);
    private final ArrayDeque<Long> downloads = new ArrayDeque<>();

    public PhotoExternalImporter(CatalogLookup lookup, PhotoImageCodec codec) {
        this(
                lookup,
                codec,
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(2))
                        .build(),
                Clock.systemUTC());
    }

    // Package-private client/clock seam; the runtime destination remains fixed and validated.
    PhotoExternalImporter(CatalogLookup lookup, PhotoImageCodec codec, HttpClient http, Clock clock) {
        this.lookup = lookup;
        this.codec = codec;
        this.http = http;
        this.clock = clock;
    }

    private synchronized void reserveDownload() {
        long now = clock.millis();
        while (!downloads.isEmpty() && downloads.peekFirst() <= now - 60000) downloads.removeFirst();
        if (downloads.size() >= 10) throw new PhotoLimitException();
        downloads.addLast(now);
    }

    public Imported fetch(UUID owner, String code) {
        if (!processing.tryAcquire()) throw new PhotoLimitException();
        CompletableFuture<HttpResponse<byte[]>> exchange = null;
        try {
            var product = lookup.externalPhoto(owner, code);
            if (product.status() != ProductLookup.Status.FOUND || product.imageUrl() == null)
                throw new NoSuchElementException();
            URI uri = URI.create(product.imageUrl());
            if (!"https".equals(uri.getScheme())
                    || !"images.openfoodfacts.org".equals(uri.getHost())
                    || uri.getPort() != -1
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !uri.getRawPath().matches("/images/products/[0-9A-Za-z_./-]+\\.(?:jpg|jpeg|png|webp)"))
                throw new IllegalArgumentException();
            reserveDownload();
            var request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Carrim/0.0.8 (https://github.com/JoaoVFAraujo/carrim-api)")
                    .GET()
                    .build();
            exchange = http.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.limiting(
                            HttpResponse.BodyHandlers.ofByteArray(), PhotoImageCodec.MAX_BYTES));
            var reply = exchange.get(5, TimeUnit.SECONDS);
            if (reply.statusCode() != 200) throw new NoSuchElementException();
            String mime = reply.headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .split(";")[0]
                    .strip()
                    .toLowerCase(java.util.Locale.ROOT);
            byte[] normalized = codec.normalize(reply.body(), mime);
            return new Imported(normalized, product.sourceUrl(), product.licenseUrl());
        } catch (PhotoLimitException limited) {
            throw limited;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Photo source unavailable");
        } catch (Exception ignored) {
            throw new NoSuchElementException();
        } finally {
            if (exchange != null && !exchange.isDone()) exchange.cancel(true);
            processing.release();
        }
    }
}

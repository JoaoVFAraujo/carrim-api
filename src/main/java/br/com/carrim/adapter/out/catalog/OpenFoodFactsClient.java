package br.com.carrim.adapter.out.catalog;

import br.com.carrim.application.catalog.ExternalProductCatalog;
import br.com.carrim.application.catalog.ProductLookup;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class OpenFoodFactsClient implements ExternalProductCatalog {
    private static final String ENDPOINT = "https://world.openfoodfacts.org/api/v3/product/";
    private static final String FIELDS =
            ".json?fields=code,product_name,product_name_pt,brands,quantity,image_front_small_url";
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Clock clock;
    private final URI endpoint;
    private final Duration timeout;

    public OpenFoodFactsClient(ObjectMapper mapper) {
        this(
                mapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                Clock.systemUTC(),
                URI.create(ENDPOINT),
                Duration.ofSeconds(5));
    }

    /** Package-private seam for local HTTP tests. Runtime endpoint is fixed HTTPS. */
    OpenFoodFactsClient(ObjectMapper mapper, HttpClient http, Clock clock, URI endpoint, Duration timeout) {
        this.mapper = mapper;
        this.http = http;
        this.clock = clock;
        this.endpoint = endpoint;
        this.timeout = timeout;
    }

    @Override
    public Reply lookup(String code) {
        if (code == null || !code.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13})"))
            throw new IllegalArgumentException("Invalid barcode");
        var request = HttpRequest.newBuilder(URI.create(endpoint + code + FIELDS))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "Carrim/0.0.8 (https://github.com/JoaoVFAraujo/carrim-api)")
                .GET()
                .build();
        var exchange = http.sendAsync(request, ignored -> new BoundedBody());
        try {
            var response = exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                int retry =
                        retryAfter(response.headers().firstValue("Retry-After").orElse("60"));
                return new Reply(
                        ProductLookup.empty(
                                code,
                                response.statusCode() == 429
                                        ? ProductLookup.Status.RATE_LIMITED
                                        : ProductLookup.Status.UNAVAILABLE),
                        retry);
            }
            if (response.statusCode() == 404) return reply(code, ProductLookup.Status.NOT_FOUND);
            if (response.statusCode() != 200) return reply(code, ProductLookup.Status.UNAVAILABLE);
            var root = mapper.readTree(response.body());
            var product = root.get("product");
            if (!"success".equals(text(root.get("status"), 32)) || product == null || !product.isObject())
                return reply(code, ProductLookup.Status.UNAVAILABLE);
            String returnedCode = text(product.get("code"), 14);
            if (returnedCode == null) returnedCode = text(root.get("code"), 14);
            if (returnedCode == null
                    || !returnedCode.matches("[0-9]{8,14}")
                    || !returnedCode.replaceFirst("^0+", "").equals(code.replaceFirst("^0+", "")))
                return reply(code, ProductLookup.Status.UNAVAILABLE);
            String name = text(product.get("product_name_pt"), 120);
            if (name == null) name = text(product.get("product_name"), 120);
            if (name == null) return reply(code, ProductLookup.Status.NOT_FOUND);
            return new Reply(
                    new ProductLookup(
                            ProductLookup.Status.FOUND,
                            ProductLookup.Source.OPEN_FOOD_FACTS,
                            code,
                            name,
                            text(product.get("brands"), 120),
                            text(product.get("quantity"), 80),
                            image(text(product.get("image_front_small_url"), 1024)),
                            "https://world.openfoodfacts.org/product/" + code,
                            "https://opendatacommons.org/licenses/odbl/1-0/",
                            null),
                    0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return reply(code, ProductLookup.Status.UNAVAILABLE);
        } catch (Exception ignored) {
            return reply(code, ProductLookup.Status.UNAVAILABLE);
        } finally {
            if (!exchange.isDone()) exchange.cancel(true);
        }
    }

    private static Reply reply(String code, ProductLookup.Status status) {
        return new Reply(ProductLookup.empty(code, status), 0);
    }

    private static String text(JsonNode node, int max) {
        if (node == null || !node.isString()) return null;
        String text = node.asString().replaceAll("[\\p{Cc}\\p{Cf}]", " ").strip();
        if (text.isEmpty()) return null;
        return text.substring(0, Math.min(text.length(), max));
    }

    private static String image(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme())
                    || !"images.openfoodfacts.org".equals(uri.getHost())
                    || uri.getRawUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !uri.getRawPath().matches("/images/products/[0-9A-Za-z_./-]+\\.(?:jpg|jpeg|png|webp)"))
                return null;
            return value;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int retryAfter(String value) {
        try {
            return (int) Math.clamp(Long.parseLong(value), 60, 300);
        } catch (NumberFormatException ignored) {
            try {
                return (int) Math.clamp(
                        Duration.between(
                                        clock.instant(),
                                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                                .toInstant())
                                .toSeconds(),
                        60,
                        300);
            } catch (RuntimeException invalid) {
                return 60;
            }
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int bytes;

        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        public void onNext(List<ByteBuffer> buffers) {
            long count = buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (count > 65536L - bytes) {
                subscription.cancel();
                delegate.onError(new IllegalArgumentException("Response too large"));
                return;
            }
            bytes += (int) count;
            delegate.onNext(buffers);
        }

        public void onError(Throwable error) {
            delegate.onError(error);
        }

        public void onComplete() {
            delegate.onComplete();
        }
    }
}

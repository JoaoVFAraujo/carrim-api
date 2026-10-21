package br.com.carrim.application.catalog;

public interface ExternalProductCatalog {
    record Reply(ProductLookup product, int retryAfterSeconds) {}

    Reply lookup(String barcode);
}

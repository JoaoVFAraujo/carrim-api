package br.com.carrim.application.catalog;

public class CatalogConflictException extends RuntimeException {
    public CatalogConflictException() {
        super("Catalog version is out of date");
    }
}

package br.com.carrim.application.shopping;

public class ShoppingConflictException extends RuntimeException {
    public ShoppingConflictException() {
        super("Shopping version is out of date");
    }
}

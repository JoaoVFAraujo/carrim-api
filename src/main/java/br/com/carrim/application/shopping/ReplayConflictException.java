package br.com.carrim.application.shopping;

public class ReplayConflictException extends RuntimeException {
    public ReplayConflictException() {
        super("Idempotency key has a different request");
    }
}

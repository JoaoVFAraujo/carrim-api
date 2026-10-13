package br.com.carrim.application.sync;

import java.util.UUID;
import java.util.function.Supplier;

public interface SyncReceiptRepository {
    record Receipt(boolean replay, String state) {}

    Receipt execute(UUID owner, UUID operation, String request, Supplier<String> apply);
}

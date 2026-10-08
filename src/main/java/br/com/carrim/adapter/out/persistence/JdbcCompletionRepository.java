package br.com.carrim.adapter.out.persistence;

import br.com.carrim.application.shopping.*;
import br.com.carrim.domain.shared.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JdbcCompletionRepository implements CompletionRepository {
    private final JdbcTemplate jdbc;
    private final ShoppingOperations operations;
    private final ShoppingRepository repository;

    public JdbcCompletionRepository(JdbcTemplate jdbc, ShoppingOperations operations, ShoppingRepository repository) {
        this.jdbc = jdbc;
        this.operations = operations;
        this.repository = repository;
    }

    @Override
    public StoredShopping complete(
            UUID owner, UUID session, long version, Instant finishedAt, Money checkout, UUID key) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(key);
        Objects.requireNonNull(finishedAt);
        Objects.requireNonNull(session);
        finishedAt = finishedAt.truncatedTo(ChronoUnit.MICROS);
        byte[] hash = hash(session + "|" + version + "|" + finishedAt.truncatedTo(ChronoUnit.MICROS) + "|"
                + (checkout == null ? "null" : checkout.cents()));
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", owner + ":complete:" + key);
        var previous = jdbc.query(
                "SELECT request_hash FROM carrim.completion_receipts WHERE user_id=? AND operation_id=?",
                (rs, n) -> rs.getBytes(1),
                owner,
                key);
        if (!previous.isEmpty()) {
            if (!MessageDigest.isEqual(previous.getFirst(), hash)) throw new ReplayConflictException();
            return repository.find(owner, session).orElseThrow();
        }
        var result = operations.complete(owner, session, version, finishedAt, checkout);
        jdbc.update(
                "INSERT INTO carrim.completion_receipts(user_id,operation_id,session_id,request_hash) VALUES (?,?,?,?)",
                owner,
                key,
                session,
                hash);
        return result;
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

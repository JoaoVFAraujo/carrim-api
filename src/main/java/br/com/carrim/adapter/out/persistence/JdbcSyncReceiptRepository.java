package br.com.carrim.adapter.out.persistence;

import br.com.carrim.application.shopping.ReplayConflictException;
import br.com.carrim.application.sync.SyncReceiptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcSyncReceiptRepository implements SyncReceiptRepository {
    private final JdbcTemplate jdbc;

    public JdbcSyncReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Receipt execute(UUID owner, UUID operation, String request, Supplier<String> apply) {
        // One owner lock also gives committed operations a stable order for later pull.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", owner + ":sync");
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var previous = jdbc.query(
                "SELECT request_hash,state_json FROM carrim.sync_receipts WHERE user_id=? AND operation_id=?",
                (rs, n) -> new Object[] {rs.getBytes(1), rs.getString(2)},
                owner,
                operation);
        if (!previous.isEmpty()) {
            if (!MessageDigest.isEqual((byte[]) previous.getFirst()[0], hash)) throw new ReplayConflictException();
            return new Receipt(true, (String) previous.getFirst()[1]);
        }
        String state = apply.get();
        jdbc.update(
                "INSERT INTO carrim.sync_receipts(user_id,operation_id,request_hash,state_json) VALUES (?,?,?,?::jsonb)",
                owner,
                operation,
                hash,
                state);
        return new Receipt(false, state);
    }
}

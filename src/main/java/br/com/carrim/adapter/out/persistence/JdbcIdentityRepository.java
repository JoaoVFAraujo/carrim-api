package br.com.carrim.adapter.out.persistence;

import br.com.carrim.application.identity.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JdbcIdentityRepository implements IdentityRepository {
    private final JdbcTemplate jdbc;

    public JdbcIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CallerIdentity issue(
            BootstrapCommand command, byte[] proofHash, byte[] accessHash, Instant now, Instant expires) {
        // Serializes creation and renewal even when the installation does not yet have a row.
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                command.installationId().toString());
        var rows = jdbc.query(
                "SELECT i.user_id,i.proof_hash,u.account_type FROM carrim.anonymous_installations i JOIN carrim.users u ON u.id=i.user_id WHERE i.id=?",
                (rs, index) -> new Existing(
                        rs.getObject("user_id", UUID.class), rs.getBytes("proof_hash"), rs.getString("account_type")),
                command.installationId());
        UUID user;
        String type;
        if (rows.isEmpty()) {
            user = UUID.randomUUID();
            type = "ANONYMOUS";
            jdbc.update(
                    "INSERT INTO carrim.users(id,account_type,created_at) VALUES (?,'ANONYMOUS',?)",
                    user,
                    Timestamp.from(now));
            jdbc.update(
                    "INSERT INTO carrim.anonymous_installations(id,user_id,proof_hash,access_token_hash,device_platform,app_version,issued_at,expires_at) VALUES (?,?,?,?,?,?,?,?)",
                    command.installationId(),
                    user,
                    proofHash,
                    accessHash,
                    command.devicePlatform(),
                    command.appVersion(),
                    Timestamp.from(now),
                    Timestamp.from(expires));
        } else {
            var existing = rows.getFirst();
            if (!MessageDigest.isEqual(existing.proof(), proofHash)) throw new IdentityProofException();
            user = existing.user();
            type = existing.type();
            jdbc.update(
                    "UPDATE carrim.anonymous_installations SET access_token_hash=?,device_platform=?,app_version=?,issued_at=?,expires_at=? WHERE id=?",
                    accessHash,
                    command.devicePlatform(),
                    command.appVersion(),
                    Timestamp.from(now),
                    Timestamp.from(expires),
                    command.installationId());
        }
        return new CallerIdentity(user, command.installationId(), type);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CallerIdentity> authenticate(byte[] accessHash, Instant now) {
        return jdbc
                .query(
                        "SELECT i.user_id,i.id,u.account_type FROM carrim.anonymous_installations i JOIN carrim.users u ON u.id=i.user_id WHERE i.access_token_hash=? AND i.expires_at>?",
                        (rs, index) -> new CallerIdentity(
                                rs.getObject("user_id", UUID.class),
                                rs.getObject("id", UUID.class),
                                rs.getString("account_type")),
                        accessHash,
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    private record Existing(UUID user, byte[] proof, String type) {}
}

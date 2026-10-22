package br.com.carrim.adapter.out.persistence;

import br.com.carrim.application.catalog.CatalogConflictException;
import br.com.carrim.application.photos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JdbcPhotoRepository implements PhotoRepository {
    private final JdbcTemplate jdbc;

    public JdbcPhotoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 1000000) throw new IllegalArgumentException();
    }

    private PhotoView view(ResultSet r, UUID owner, boolean admin) throws SQLException {
        boolean own = owner != null && owner.equals(r.getObject("user_id", UUID.class));
        return new PhotoView(
                r.getObject("id", UUID.class),
                r.getString("barcode"),
                own || admin ? r.getObject("item_id", UUID.class) : null,
                r.getString("name"),
                r.getString("source"),
                r.getString("status"),
                r.getLong("version"),
                r.getString("content_hash"),
                r.getString("source_url"),
                r.getString("license_url"),
                r.getTimestamp("updated_at").toInstant(),
                own);
    }

    private void target(UUID owner, String code, UUID itemId) {
        if (owner == null || (code == null) == (itemId == null)) throw new IllegalArgumentException();
        if (code != null) {
            if (!code.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13})")) throw new IllegalArgumentException();
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM carrim.products WHERE user_id=? AND barcode=?",
                            Integer.class,
                            owner,
                            code)
                    == 0) throw new NoSuchElementException();
        }
        if (itemId != null
                && jdbc.queryForObject(
                                "SELECT count(*) FROM carrim.shopping_items WHERE user_id=? AND id=?",
                                Integer.class,
                                owner,
                                itemId)
                        == 0) throw new NoSuchElementException();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateTarget(UUID owner, String code, UUID itemId) {
        target(owner, code, itemId);
    }

    private void ownerLock(UUID owner) {
        if (jdbc.query("SELECT id FROM carrim.users WHERE id=? FOR UPDATE", (r, n) -> r.getObject(1, UUID.class), owner)
                .isEmpty()) throw new NoSuchElementException();
    }

    private List<PhotoView> byId(UUID owner, UUID id) {
        return jdbc.query(
                "SELECT id,user_id,barcode,item_id,name,source,status,version,content_hash,source_url,license_url,updated_at FROM carrim.product_photos WHERE id=? AND user_id=?",
                (r, n) -> view(r, owner, false),
                id,
                owner);
    }

    @Override
    public PhotoView create(
            UUID owner,
            UUID id,
            String name,
            String code,
            UUID itemId,
            String source,
            String inputHash,
            byte[] bytes,
            String sourceUrl,
            String licenseUrl) {
        ownerLock(owner);
        var prior = jdbc.query(
                "SELECT user_id,input_hash FROM carrim.product_photos WHERE id=?",
                (r, n) -> new String[] {r.getObject(1, UUID.class).toString(), r.getString(2)},
                id);
        if (!prior.isEmpty()) {
            if (!prior.getFirst()[0].equals(owner.toString())) throw new NoSuchElementException();
            if (!MessageDigest.isEqual(
                    prior.getFirst()[1].getBytes(StandardCharsets.US_ASCII),
                    inputHash.getBytes(StandardCharsets.US_ASCII))) throw new CatalogConflictException();
            return byId(owner, id).getFirst();
        }
        target(owner, code, itemId);
        if (jdbc.queryForObject(
                                "SELECT count(*) FROM carrim.product_photos WHERE user_id=? AND status<>'DELETED'",
                                Integer.class,
                                owner)
                        >= 100
                || jdbc.queryForObject(
                                "SELECT count(*) FROM carrim.product_photos WHERE user_id=?", Integer.class, owner)
                        >= 1000) throw new PhotoLimitException();
        if (name == null
                || name.isBlank()
                || name.length() > 120
                || name.chars().anyMatch(c -> Character.isISOControl(c))) throw new IllegalArgumentException();
        String status = source.equals("USER") ? "PENDING" : "EXTERNAL";
        jdbc.update(
                "INSERT INTO carrim.product_photos(id,user_id,barcode,item_id,name,source,status,content,input_hash,content_hash,source_url,license_url) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                owner,
                code,
                itemId,
                name.strip(),
                source,
                status,
                bytes,
                inputHash,
                hash(bytes),
                sourceUrl,
                licenseUrl);
        return byId(owner, id).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PhotoView> replay(UUID owner, UUID id, String inputHash) {
        var hashes = jdbc.query(
                "SELECT input_hash FROM carrim.product_photos WHERE user_id=? AND id=?",
                (r, n) -> r.getString(1),
                owner,
                id);
        if (hashes.isEmpty()) return Optional.empty();
        if (!MessageDigest.isEqual(
                hashes.getFirst().getBytes(StandardCharsets.US_ASCII), inputHash.getBytes(StandardCharsets.US_ASCII)))
            throw new CatalogConflictException();
        return Optional.of(byId(owner, id).getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public PhotoView metadata(UUID owner, UUID id) {
        return jdbc
                .query(
                        "SELECT id,user_id,barcode,item_id,name,source,status,version,content_hash,source_url,license_url,updated_at FROM carrim.product_photos WHERE id=? AND (user_id=? OR status='APPROVED')",
                        (r, n) -> view(r, owner, false),
                        id,
                        owner)
                .stream()
                .findFirst()
                .orElseThrow(NoSuchElementException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] content(UUID owner, UUID id, boolean admin) {
        var result = jdbc.query(
                "SELECT content FROM carrim.product_photos WHERE id=? AND status<>'DELETED' AND (? OR user_id=? OR status='APPROVED')",
                (r, n) -> r.getBytes(1),
                id,
                admin,
                owner);
        if (result.isEmpty()) throw new NoSuchElementException();
        return result.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoView> mine(UUID owner, int limit, int offset) {
        page(limit, offset);
        return jdbc.query(
                "SELECT id,user_id,barcode,item_id,name,source,status,version,content_hash,source_url,license_url,updated_at FROM carrim.product_photos WHERE user_id=? ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",
                (r, n) -> view(r, owner, false),
                owner,
                limit,
                offset);
    }

    @Override
    @Transactional(readOnly = true)
    public PhotoView approved(String code) {
        if (code == null || !code.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13})")) throw new IllegalArgumentException();
        return jdbc
                .query(
                        "SELECT id,user_id,barcode,item_id,name,source,status,version,content_hash,source_url,license_url,updated_at FROM carrim.product_photos WHERE barcode=? AND status='APPROVED' ORDER BY reviewed_at DESC,id LIMIT 1",
                        (r, n) -> view(r, null, false),
                        code)
                .stream()
                .findFirst()
                .orElseThrow(NoSuchElementException::new);
    }

    @Override
    public void delete(UUID owner, UUID id) {
        ownerLock(owner);
        if (byId(owner, id).isEmpty()) throw new NoSuchElementException();
        jdbc.update(
                "UPDATE carrim.product_photos SET status='DELETED',content=NULL,version=version+1,updated_at=clock_timestamp() WHERE id=? AND user_id=? AND status<>'DELETED'",
                id,
                owner);
        jdbc.update(
                "UPDATE carrim.photo_preferences SET photo_id=NULL,hidden=true,version=version+1,updated_at=clock_timestamp() WHERE user_id=? AND photo_id=?",
                owner,
                id);
    }

    private PhotoPreference pref(ResultSet r) throws SQLException {
        return new PhotoPreference(
                r.getString("preference_key"),
                r.getString("barcode"),
                r.getObject("item_id", UUID.class),
                r.getObject("photo_id", UUID.class),
                r.getBoolean("hidden"),
                r.getLong("version"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoPreference> preferences(UUID owner, int limit, int offset) {
        page(limit, offset);
        return jdbc.query(
                "SELECT * FROM carrim.photo_preferences WHERE user_id=? ORDER BY preference_key LIMIT ? OFFSET ?",
                (r, n) -> pref(r),
                owner,
                limit,
                offset);
    }

    @Override
    @Transactional(readOnly = true)
    public PhotoPreference preference(UUID owner, String key) {
        return jdbc
                .query(
                        "SELECT * FROM carrim.photo_preferences WHERE user_id=? AND preference_key=?",
                        (r, n) -> pref(r),
                        owner,
                        key)
                .stream()
                .findFirst()
                .orElseThrow(NoSuchElementException::new);
    }

    @Override
    public PhotoPreference preference(
            UUID owner, String key, String code, UUID itemId, UUID photoId, boolean hidden, Long version) {
        ownerLock(owner);
        target(owner, code, itemId);
        if (code != null && itemId != null
                || !Objects.equals(key, code != null ? "barcode:" + code : "item:" + itemId)
                || hidden && photoId != null) throw new IllegalArgumentException();
        if (photoId != null) {
            var accessible = jdbc.queryForObject(
                    "SELECT count(*) FROM carrim.product_photos WHERE id=? AND status<>'DELETED' AND (user_id=? OR status='APPROVED') "
                            + "AND barcode IS NOT DISTINCT FROM ? AND item_id IS NOT DISTINCT FROM ?",
                    Integer.class,
                    photoId,
                    owner,
                    code,
                    itemId);
            if (accessible == 0) throw new NoSuchElementException();
        }
        var previous = jdbc.query(
                "SELECT * FROM carrim.photo_preferences WHERE user_id=? AND preference_key=?",
                (r, n) -> pref(r),
                owner,
                key);
        if (previous.isEmpty()) {
            if (version != null) throw new CatalogConflictException();
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM carrim.photo_preferences WHERE user_id=?", Integer.class, owner)
                    >= 200) throw new PhotoLimitException();
            jdbc.update(
                    "INSERT INTO carrim.photo_preferences(user_id,preference_key,barcode,item_id,photo_id,hidden) VALUES (?,?,?,?,?,?)",
                    owner,
                    key,
                    code,
                    itemId,
                    photoId,
                    hidden);
        } else {
            var old = previous.getFirst();
            if (Objects.equals(old.photoId(), photoId) && old.hidden() == hidden) return old;
            if (version == null || old.version() != version) throw new CatalogConflictException();
            jdbc.update(
                    "UPDATE carrim.photo_preferences SET photo_id=?,hidden=?,version=version+1,updated_at=clock_timestamp() WHERE user_id=? AND preference_key=?",
                    photoId,
                    hidden,
                    owner,
                    key);
        }
        // The current choice is authoritative; obsolete pending contributions cannot later be published.
        // Pending content was only accessible to its owner; protect their active legacy references.
        jdbc.update(
                "UPDATE carrim.product_photos SET status='DELETED',content=NULL,version=version+1,updated_at=clock_timestamp() "
                        + "WHERE user_id=? AND source='USER' AND status='PENDING' AND id IS DISTINCT FROM ? "
                        + "AND barcode IS NOT DISTINCT FROM ? AND item_id IS NOT DISTINCT FROM ? "
                        + "AND NOT EXISTS (SELECT 1 FROM carrim.photo_preferences p WHERE p.user_id=carrim.product_photos.user_id AND p.photo_id=carrim.product_photos.id AND p.hidden=false)",
                owner,
                photoId,
                code,
                itemId);
        return jdbc.query(
                        "SELECT * FROM carrim.photo_preferences WHERE user_id=? AND preference_key=?",
                        (r, n) -> pref(r),
                        owner,
                        key)
                .getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhotoView> reviewQueue(String status, int limit, int offset) {
        page(limit, offset);
        if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) throw new IllegalArgumentException();
        return jdbc.query(
                "SELECT id,user_id,barcode,item_id,name,source,status,version,content_hash,source_url,license_url,updated_at FROM carrim.product_photos WHERE source='USER' AND status=? ORDER BY updated_at,id LIMIT ? OFFSET ?",
                (r, n) -> view(r, null, true),
                status,
                limit,
                offset);
    }

    @Override
    public PhotoView decide(UUID id, String decision, long version, String reason) {
        if (version < 0
                || !Set.of("APPROVE", "REJECT").contains(decision)
                || reason != null && (reason.length() > 200 || reason.chars().anyMatch(c -> Character.isISOControl(c))))
            throw new IllegalArgumentException();
        var owners = jdbc.query(
                "SELECT user_id FROM carrim.product_photos WHERE id=?", (r, n) -> r.getObject(1, UUID.class), id);
        if (owners.isEmpty()) throw new NoSuchElementException();
        // All media mutations lock owner before photo, including moderation.
        ownerLock(owners.getFirst());
        var rows = jdbc.query(
                "SELECT id,user_id,barcode,item_id,name,source,status,version,content_hash,source_url,license_url,updated_at FROM carrim.product_photos WHERE id=? FOR UPDATE",
                (r, n) -> view(r, null, true),
                id);
        if (rows.isEmpty()) throw new NoSuchElementException();
        var old = rows.getFirst();
        if (!old.source().equals("USER")
                || old.version() != version
                || !old.status().equals("PENDING")
                        && !(decision.equals("REJECT") && old.status().equals("APPROVED")))
            throw new CatalogConflictException();
        if (decision.equals("APPROVE")
                && jdbc.queryForObject(
                                "SELECT count(*) FROM carrim.photo_preferences WHERE user_id=? AND photo_id=? AND hidden=false "
                                        + "AND barcode IS NOT DISTINCT FROM ? AND item_id IS NOT DISTINCT FROM ?",
                                Integer.class,
                                owners.getFirst(),
                                id,
                                old.barcode(),
                                old.itemId())
                        == 0) throw new CatalogConflictException();
        jdbc.update(
                "UPDATE carrim.product_photos SET status=?,version=version+1,reviewed_at=clock_timestamp(),updated_at=clock_timestamp(),review_reason=? WHERE id=?",
                decision.equals("APPROVE") ? "APPROVED" : "REJECTED",
                reason,
                id);
        jdbc.update(
                "INSERT INTO carrim.photo_review_audit(photo_id,decision,previous_version,reason) VALUES (?,?,?,?)",
                id,
                decision,
                version,
                reason);
        return jdbc.query(
                        "SELECT id,user_id,barcode,item_id,name,source,status,version,content_hash,source_url,license_url,updated_at FROM carrim.product_photos WHERE id=?",
                        (r, n) -> view(r, null, true),
                        id)
                .getFirst();
    }

    public static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

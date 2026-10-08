package br.com.carrim.adapter.out.persistence;

import br.com.carrim.application.shopping.PriceHistoryEntry;
import br.com.carrim.application.shopping.ShoppingConflictException;
import br.com.carrim.application.shopping.ShoppingRepository;
import br.com.carrim.application.shopping.ShoppingSummary;
import br.com.carrim.application.shopping.StoredShopping;
import br.com.carrim.domain.pricing.ComparisonBasis;
import br.com.carrim.domain.shared.Money;
import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.domain.shopping.PricingType;
import br.com.carrim.domain.shopping.ShoppingItem;
import br.com.carrim.domain.shopping.ShoppingSession;
import br.com.carrim.domain.shopping.ShoppingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit SQL keeps aggregate writes and immutable history in one transaction. */
@Component
@Transactional
public class JdbcShoppingRepository implements ShoppingRepository {
    private final JdbcTemplate jdbc;

    public JdbcShoppingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<StoredShopping> active(UUID owner) {
        var ids = jdbc.query(
                "SELECT id FROM carrim.shopping_sessions WHERE user_id=? AND status='ACTIVE'",
                (rs, n) -> rs.getObject(1, UUID.class),
                Objects.requireNonNull(owner));
        return ids.isEmpty() ? Optional.empty() : load(owner, ids.getFirst(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShoppingSummary> list(UUID owner, ShoppingStatus status, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 1000000)
            throw new IllegalArgumentException("Invalid page");
        return jdbc.query(
                """
                SELECT s.*, coalesce(sum(i.subtotal_cents),0) AS total,count(i.id) AS item_count
                FROM carrim.shopping_sessions s LEFT JOIN carrim.shopping_items i ON i.session_id=s.id
                WHERE s.user_id=? AND (?::varchar IS NULL OR s.status=?) GROUP BY s.id
                ORDER BY s.started_at DESC,s.id LIMIT ? OFFSET ?
                """,
                (rs, n) -> new ShoppingSummary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("supermarket_id", UUID.class),
                        ShoppingStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("finished_at") == null
                                ? null
                                : rs.getTimestamp("finished_at").toInstant(),
                        rs.getObject("budget_cents", Long.class),
                        rs.getObject("checkout_total_cents", Long.class),
                        rs.getLong("total"),
                        rs.getLong("item_count"),
                        rs.getLong("version")),
                Objects.requireNonNull(owner),
                status == null ? null : status.name(),
                status == null ? null : status.name(),
                limit,
                offset);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PriceHistoryEntry> lastPrice(UUID owner, UUID product, UUID market, MeasurementType measurement) {
        return jdbc
                .query(
                        """
                SELECT i.*,o.supermarket_id,o.observed_at,o.comparison_basis,o.normalized_price_cents
                FROM carrim.price_observations o JOIN carrim.shopping_items i ON i.id=o.id
                WHERE o.user_id=? AND i.product_id=? AND o.supermarket_id=? AND i.measurement_type=? AND i.pricing_type='REGULAR'
                ORDER BY o.observed_at DESC,o.id DESC LIMIT 1
                """,
                        (rs, n) -> new PriceHistoryEntry(
                                rs.getObject("id", UUID.class),
                                rs.getObject("session_id", UUID.class),
                                rs.getObject("supermarket_id", UUID.class),
                                rs.getTimestamp("observed_at").toInstant(),
                                item(rs),
                                ComparisonBasis.valueOf(rs.getString("comparison_basis")),
                                new Money(rs.getLong("normalized_price_cents"))),
                        Objects.requireNonNull(owner),
                        product,
                        market,
                        measurement.name())
                .stream()
                .findFirst();
    }

    @Override
    public StoredShopping create(UUID owner, ShoppingSession session) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(session);
        if (session.status() != ShoppingStatus.ACTIVE) throw new IllegalArgumentException("Create requires ACTIVE");
        jdbc.update(
                """
                INSERT INTO carrim.shopping_sessions(id,user_id,supermarket_id,budget_cents,status,started_at)
                VALUES (?,?,?,?,'ACTIVE',?)
                """,
                session.id(),
                owner,
                session.supermarketId(),
                cents(session.budget()),
                Timestamp.from(session.startedAt()));
        insertItems(owner, session);
        return load(owner, session.id(), false).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<StoredShopping> find(UUID owner, UUID sessionId) {
        return load(owner, sessionId, false);
    }

    @Override
    public StoredShopping mutate(
            UUID owner, UUID sessionId, long expectedVersion, UnaryOperator<ShoppingSession> change) {
        StoredShopping current = load(owner, sessionId, true).orElseThrow(NoSuchElementException::new);
        if (current.version() != expectedVersion) throw new ShoppingConflictException();
        ShoppingSession previous = current.value();
        if (previous.status() != ShoppingStatus.ACTIVE) throw new IllegalStateException("Closed session is immutable");
        ShoppingSession next = Objects.requireNonNull(change).apply(previous);
        Objects.requireNonNull(next);
        if (!previous.id().equals(next.id())
                || !previous.supermarketId().equals(next.supermarketId())
                || !previous.startedAt().equals(next.startedAt()))
            throw new IllegalArgumentException("Session identity cannot change");
        if (!previous.items().equals(next.items())) {
            jdbc.update("DELETE FROM carrim.shopping_items WHERE user_id=? AND session_id=?", owner, sessionId);
            insertItems(owner, next);
        }
        int changed = jdbc.update(
                """
                UPDATE carrim.shopping_sessions SET budget_cents=?, status=?, finished_at=?, checkout_total_cents=?, version=version+1
                WHERE user_id=? AND id=? AND version=?
                """,
                cents(next.budget()),
                next.status().name(),
                next.finishedAt() == null ? null : Timestamp.from(next.finishedAt()),
                cents(next.checkoutTotal()),
                owner,
                sessionId,
                expectedVersion);
        if (changed != 1) throw new ShoppingConflictException();
        if (next.status() == ShoppingStatus.COMPLETED) {
            for (ShoppingItem item : next.items()) {
                jdbc.update(
                        "INSERT INTO carrim.price_observations(id,user_id,session_id) VALUES (?,?,?)",
                        item.id(),
                        owner,
                        sessionId);
            }
        }
        return load(owner, sessionId, false).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<PriceHistoryEntry> history(UUID owner, UUID sessionId) {
        return jdbc.query(
                """
                SELECT i.*, o.supermarket_id, o.observed_at, o.comparison_basis, o.normalized_price_cents
                FROM carrim.price_observations o JOIN carrim.shopping_items i ON i.id=o.id
                WHERE o.user_id=? AND o.session_id=? ORDER BY i.position,i.id
                """,
                (rs, number) -> new PriceHistoryEntry(
                        rs.getObject("id", UUID.class),
                        sessionId,
                        rs.getObject("supermarket_id", UUID.class),
                        rs.getTimestamp("observed_at").toInstant(),
                        item(rs),
                        ComparisonBasis.valueOf(rs.getString("comparison_basis")),
                        new Money(rs.getLong("normalized_price_cents"))),
                Objects.requireNonNull(owner),
                Objects.requireNonNull(sessionId));
    }

    private Optional<StoredShopping> load(UUID owner, UUID sessionId, boolean lock) {
        var rows = jdbc.query(
                "SELECT * FROM carrim.shopping_sessions WHERE user_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, number) -> new StoredShopping(
                        new ShoppingSession(
                                sessionId,
                                rs.getObject("supermarket_id", UUID.class),
                                money(rs, "budget_cents"),
                                ShoppingStatus.valueOf(rs.getString("status")),
                                rs.getTimestamp("started_at").toInstant(),
                                rs.getTimestamp("finished_at") == null
                                        ? null
                                        : rs.getTimestamp("finished_at").toInstant(),
                                money(rs, "checkout_total_cents"),
                                jdbc.query(
                                        "SELECT * FROM carrim.shopping_items WHERE user_id=? AND session_id=? ORDER BY position,id",
                                        (line, index) -> item(line),
                                        owner,
                                        sessionId)),
                        rs.getLong("version")),
                Objects.requireNonNull(owner),
                Objects.requireNonNull(sessionId));
        return rows.stream().findFirst();
    }

    private void insertItems(UUID owner, ShoppingSession session) {
        int position = 0;
        for (ShoppingItem item : session.items()) {
            jdbc.update(
                    """
                    INSERT INTO carrim.shopping_items(id,user_id,session_id,product_id,product_name_snapshot,
                    measurement_type,pricing_type,reference_price_cents,quantity,weight_grams,bundle_quantity,position)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    item.id(),
                    owner,
                    session.id(),
                    item.productId(),
                    item.productNameSnapshot(),
                    item.measurementType().name(),
                    item.pricingType().name(),
                    item.referencePrice().cents(),
                    item.quantity(),
                    item.weightGrams(),
                    item.bundleQuantity(),
                    position++);
        }
    }

    private static ShoppingItem item(ResultSet rs) throws SQLException {
        return new ShoppingItem(
                rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name_snapshot"),
                MeasurementType.valueOf(rs.getString("measurement_type")),
                PricingType.valueOf(rs.getString("pricing_type")),
                new Money(rs.getLong("reference_price_cents")),
                rs.getInt("quantity"),
                rs.getObject("weight_grams", Integer.class),
                rs.getObject("bundle_quantity", Integer.class));
    }

    private static Money money(ResultSet rs, String column) throws SQLException {
        Long value = rs.getObject(column, Long.class);
        return value == null ? null : new Money(value);
    }

    private static Long cents(Money money) {
        return money == null ? null : money.cents();
    }
}

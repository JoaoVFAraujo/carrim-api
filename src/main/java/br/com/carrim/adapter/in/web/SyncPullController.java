package br.com.carrim.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncPullController {
    public record Page(List<SyncController.Change> changes, String cursor, boolean hasMore) {}

    private record Event(long sequence, String type, UUID id) {}

    private final JdbcTemplate jdbc;
    private final CatalogController catalog;
    private final ShoppingController shopping;
    private final ObjectMapper mapper;

    public SyncPullController(
            JdbcTemplate jdbc, CatalogController catalog, ShoppingController shopping, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.shopping = shopping;
        this.mapper = mapper;
    }

    @GetMapping("/pull")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page pull(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException();
        UUID owner = ApiActor.owner(actor);
        long after = decode(owner, cursor);
        var heads = jdbc.query(
                "SELECT sequence_number FROM carrim.sync_heads WHERE user_id=?", (rs, n) -> rs.getLong(1), owner);
        long head = heads.isEmpty() ? 0 : heads.getFirst();
        if (after > head) throw new IllegalArgumentException();
        var events = jdbc.query(
                "SELECT sequence_number,aggregate_type,aggregate_id FROM carrim.sync_changes WHERE user_id=? AND sequence_number>? ORDER BY sequence_number LIMIT ?",
                (rs, n) -> new Event(rs.getLong(1), rs.getString(2), rs.getObject(3, UUID.class)),
                owner,
                after,
                limit);
        var changes = new ArrayList<SyncController.Change>();
        for (var event : events) {
            Object snapshot =
                    switch (event.type()) {
                        case "PRODUCT" -> catalog.product(actor, event.id());
                        case "SUPERMARKET" -> catalog.market(actor, event.id());
                        case "SHOPPING_SESSION" -> shopping.get(actor, event.id());
                        default -> throw new IllegalStateException();
                    };
            changes.add(new SyncController.Change(event.type(), event.id(), mapper.valueToTree(snapshot)));
        }
        long next = events.isEmpty() ? after : events.getLast().sequence();
        return new Page(changes, encode(owner, next), next < head);
    }

    private long decode(UUID owner, String cursor) {
        if (cursor == null) return 0;
        if (cursor.length() > 128) throw new IllegalArgumentException();
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split(":", -1);
            if (parts.length != 2 || !parts[0].equals(owner.toString())) throw new IllegalArgumentException();
            long seq = Long.parseLong(parts[1]);
            if (seq < 0) throw new IllegalArgumentException();
            return seq;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }

    private String encode(UUID owner, long sequence) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((owner + ":" + sequence).getBytes(StandardCharsets.UTF_8));
    }
}

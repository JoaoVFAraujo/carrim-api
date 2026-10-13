package br.com.carrim.adapter.in.web;

import br.com.carrim.application.catalog.CatalogConflictException;
import br.com.carrim.application.shopping.ReplayConflictException;
import br.com.carrim.application.shopping.ShoppingConflictException;
import br.com.carrim.application.sync.SyncReceiptRepository;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    public record Operation(
            @NotNull UUID operationId,
            @NotBlank String aggregateType,
            @NotNull UUID aggregateId,
            @NotBlank String operation,
            @Min(0) Long baseVersion,
            @NotNull JsonNode payload) {}

    public record Push(@NotEmpty @Size(max = 50) List<@NotNull @Valid Operation> operations) {}

    public record Outcome(UUID operationId, String status, String code, JsonNode state) {}

    public record Change(String aggregateType, UUID aggregateId, JsonNode snapshot) {}

    private final SyncReceiptRepository receipts;
    private final CatalogController catalog;
    private final ShoppingController shopping;
    private final ObjectMapper mapper;
    private final Validator validator;

    public SyncController(
            SyncReceiptRepository receipts,
            CatalogController catalog,
            ShoppingController shopping,
            ObjectMapper mapper,
            Validator validator) {
        this.receipts = receipts;
        this.catalog = catalog;
        this.shopping = shopping;
        this.mapper = mapper;
        this.validator = validator;
    }

    @PostMapping("/push")
    public List<Outcome> push(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @Valid @RequestBody Push input) {
        var outcomes = new ArrayList<Outcome>();
        boolean blocked = false;
        for (var op : input.operations()) {
            if (blocked) {
                outcomes.add(new Outcome(op.operationId(), "NOT_PROCESSED", null, null));
                continue;
            }
            try {
                if (!op.payload().isObject() || op.payload().toString().length() > 32768)
                    throw new IllegalArgumentException();
                String request = mapper.writeValueAsString(new Operation(
                        op.operationId(),
                        op.aggregateType(),
                        op.aggregateId(),
                        op.operation(),
                        op.baseVersion(),
                        canonical(op.payload())));
                var result = receipts.execute(
                        ApiActor.owner(actor),
                        op.operationId(),
                        request,
                        () -> mapper.writeValueAsString(new Change(
                                op.aggregateType(), op.aggregateId(), mapper.valueToTree(apply(actor, op)))));
                outcomes.add(new Outcome(
                        op.operationId(),
                        result.replay() ? "ALREADY_APPLIED" : "APPLIED",
                        null,
                        mapper.readTree(result.state())));
            } catch (ReplayConflictException e) {
                outcomes.add(new Outcome(op.operationId(), "REJECTED", "IDEMPOTENCY_CONFLICT", null));
                blocked = true;
            } catch (CatalogConflictException | ShoppingConflictException e) {
                outcomes.add(new Outcome(op.operationId(), "CONFLICT", "VERSION_CONFLICT", current(actor, op)));
                blocked = true;
            } catch (IllegalStateException | DataIntegrityViolationException e) {
                outcomes.add(new Outcome(op.operationId(), "CONFLICT", "STATE_CONFLICT", current(actor, op)));
                blocked = true;
            } catch (IllegalArgumentException | NoSuchElementException e) {
                outcomes.add(new Outcome(op.operationId(), "REJECTED", "INVALID_OPERATION", null));
                blocked = true;
            }
        }
        return outcomes;
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            var keys = new TreeSet<String>();
            node.properties().forEach(e -> keys.add(e.getKey()));
            keys.forEach(key -> result.set(key, canonical(node.get(key))));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node;
    }

    private <T> T input(JsonNode payload, Class<T> type) {
        T value;
        try {
            value = mapper.treeToValue(payload, type);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("Invalid payload");
        }
        if (value == null) throw new IllegalArgumentException("Missing payload");
        if (!validator.validate(value).isEmpty()) throw new IllegalArgumentException();
        return value;
    }

    private long version(Operation op) {
        if (op.baseVersion() == null) throw new IllegalArgumentException();
        return op.baseVersion();
    }

    private Object apply(OAuth2AuthenticatedPrincipal actor, Operation op) {
        var p = op.payload().deepCopy();
        if (op.aggregateType().equals("PRODUCT")) {
            var obj = (tools.jackson.databind.node.ObjectNode) p;
            obj.put("id", op.aggregateId().toString());
            if (op.operation().equals("CREATE"))
                return catalog.create(actor, input(obj, CatalogController.ProductInput.class));
            if (op.operation().equals("UPDATE")) {
                obj.remove("id");
                obj.put("version", version(op));
                return catalog.update(actor, op.aggregateId(), input(obj, CatalogController.ProductUpdate.class));
            }
        }
        if (op.aggregateType().equals("SUPERMARKET")) {
            var obj = (tools.jackson.databind.node.ObjectNode) p;
            obj.put("id", op.aggregateId().toString());
            if (op.operation().equals("CREATE"))
                return catalog.createMarket(actor, input(obj, CatalogController.MarketInput.class));
            if (op.operation().equals("UPDATE")) {
                obj.remove("id");
                obj.put("version", version(op));
                return catalog.updateMarket(actor, op.aggregateId(), input(obj, CatalogController.MarketUpdate.class));
            }
        }
        if (op.aggregateType().equals("SHOPPING_SESSION")) {
            var obj = (tools.jackson.databind.node.ObjectNode) p;
            if (op.operation().equals("CREATE")) {
                obj.put("id", op.aggregateId().toString());
                return shopping.start(actor, input(obj, ShoppingController.StartInput.class));
            }
            obj.put("version", version(op));
            return switch (op.operation()) {
                case "UPDATE" ->
                    shopping.budget(actor, op.aggregateId(), input(obj, ShoppingController.BudgetInput.class));
                case "COMPLETE" ->
                    shopping.complete(
                            actor,
                            op.aggregateId(),
                            op.operationId(),
                            input(obj, ShoppingController.CompleteInput.class));
                case "CANCEL" ->
                    shopping.cancel(actor, op.aggregateId(), input(obj, ShoppingController.CancelInput.class));
                default -> throw new IllegalArgumentException();
            };
        }
        if (op.aggregateType().equals("SHOPPING_ITEM")) {
            var session = UUID.fromString(p.path("sessionId").asString());
            if (op.operation().equals("DELETE")) return shopping.remove(actor, session, op.aggregateId(), version(op));
            var item = input(p.path("item"), ShoppingController.ItemInput.class);
            if (!op.aggregateId().equals(item.id())) throw new IllegalArgumentException();
            var write = new ShoppingController.ItemWrite(version(op), item);
            return switch (op.operation()) {
                case "CREATE" -> shopping.add(actor, session, write);
                case "UPDATE" -> shopping.replace(actor, session, op.aggregateId(), write);
                default -> throw new IllegalArgumentException();
            };
        }
        throw new IllegalArgumentException();
    }

    private JsonNode current(OAuth2AuthenticatedPrincipal actor, Operation op) {
        try {
            Object value =
                    switch (op.aggregateType()) {
                        case "PRODUCT" -> catalog.product(actor, op.aggregateId());
                        case "SUPERMARKET" -> catalog.market(actor, op.aggregateId());
                        case "SHOPPING_SESSION" -> shopping.get(actor, op.aggregateId());
                        case "SHOPPING_ITEM" ->
                            shopping.get(
                                    actor,
                                    UUID.fromString(
                                            op.payload().path("sessionId").asString()));
                        default -> null;
                    };
            return mapper.valueToTree(value);
        } catch (NoSuchElementException | IllegalArgumentException e) {
            return null;
        }
    }
}

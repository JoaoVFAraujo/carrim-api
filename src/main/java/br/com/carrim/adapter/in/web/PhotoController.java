package br.com.carrim.adapter.in.web;

import br.com.carrim.adapter.out.catalog.PhotoExternalImporter;
import br.com.carrim.adapter.out.catalog.PhotoImageCodec;
import br.com.carrim.adapter.out.persistence.JdbcPhotoRepository;
import br.com.carrim.application.photos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/photos")
public class PhotoController {
    private final PhotoRepository photos;
    private final PhotoImageCodec codec;
    private final PhotoExternalImporter external;
    private final PhotoRateLimit rate;

    public PhotoController(
            PhotoRepository photos, PhotoImageCodec codec, PhotoExternalImporter external, PhotoRateLimit rate) {
        this.photos = photos;
        this.codec = codec;
        this.external = external;
        this.rate = rate;
    }

    public record Upload(
            @NotBlank @Size(max = 120) String name,
            String barcode,
            UUID itemId,
            @NotBlank String contentType,
            @NotBlank @Size(max = 682668) String imageBase64) {
        @AssertTrue
        public boolean isTargetValid() {
            return (barcode == null) != (itemId == null);
        }
    }

    public record External(
            @NotBlank @Size(max = 120) String name,
            @NotBlank String barcode,
            UUID itemId) {
        @AssertTrue
        public boolean isTargetValid() {
            return itemId == null;
        }
    }

    private static String normalizeKey(String key) {
        if (key.startsWith("barcode-")) return "barcode:" + key.substring(8);
        if (key.startsWith("item-")) return "item:" + key.substring(5);
        return key;
    }

    public record Preference(String barcode, UUID itemId, UUID photoId, boolean hidden, Long version) {}

    private static String digest(String source, String name, String code, UUID item, String payload) {
        return JdbcPhotoRepository.hash(
                (source + "\u0000" + name + "\u0000" + code + "\u0000" + item + "\u0000" + payload)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static <T> ResponseEntity<T> response(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PhotoView> upload(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody Upload input) {
        UUID owner = ApiActor.owner(actor);
        rate.write(owner);
        if (input.barcode() != null) ApiInputs.barcode(input.barcode());
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(input.imageBase64());
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException();
        }
        if (!Base64.getEncoder().encodeToString(bytes).equals(input.imageBase64()))
            throw new IllegalArgumentException();
        var normalized = codec.normalize(bytes, input.contentType());
        return response(photos.create(
                owner,
                id,
                input.name(),
                input.barcode(),
                input.itemId(),
                "USER",
                digest(
                        "USER",
                        input.name(),
                        input.barcode(),
                        input.itemId(),
                        input.contentType() + ":" + input.imageBase64()),
                normalized,
                null,
                null));
    }

    @PostMapping("/{id}/external")
    public ResponseEntity<PhotoView> external(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable UUID id,
            @Valid @RequestBody External input) {
        UUID owner = ApiActor.owner(actor);
        rate.write(owner);
        ApiInputs.barcode(input.barcode());
        String digest = digest("OPEN_FOOD_FACTS", input.name(), input.barcode(), input.itemId(), "");
        var replay = photos.replay(owner, id, digest);
        if (replay.isPresent()) return response(replay.get());
        photos.validateTarget(owner, input.barcode(), input.itemId());
        var imported = external.fetch(owner, input.barcode());
        return response(photos.create(
                owner,
                id,
                input.name(),
                input.barcode(),
                input.itemId(),
                "OPEN_FOOD_FACTS",
                digest,
                imported.bytes(),
                imported.sourceUrl(),
                imported.licenseUrl()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhotoView> metadata(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable UUID id) {
        return response(photos.metadata(ApiActor.owner(actor), id));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable UUID id) {
        return image(photos.content(ApiActor.owner(actor), id, false));
    }

    public static ResponseEntity<byte[]> image(byte[] content) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<PhotoView>> mine(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return response(photos.mine(ApiActor.owner(actor), limit, offset));
    }

    @GetMapping("/approved/{barcode}")
    public ResponseEntity<PhotoView> approved(@PathVariable String barcode) {
        ApiInputs.barcode(barcode);
        return response(photos.approved(barcode));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<PhotoView> delete(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable UUID id) {
        UUID owner = ApiActor.owner(actor);
        rate.write(owner);
        photos.delete(owner, id);
        return response(photos.metadata(owner, id));
    }

    @GetMapping("/preferences")
    public ResponseEntity<List<PhotoPreference>> preferences(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return response(photos.preferences(ApiActor.owner(actor), limit, offset));
    }

    @GetMapping("/preferences/{key}")
    public ResponseEntity<PhotoPreference> preference(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor, @PathVariable String key) {
        return response(photos.preference(ApiActor.owner(actor), normalizeKey(key)));
    }

    @PutMapping("/preferences/{key}")
    public ResponseEntity<PhotoPreference> preference(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal actor,
            @PathVariable String key,
            @RequestBody Preference input) {
        UUID owner = ApiActor.owner(actor);
        rate.write(owner);
        return response(photos.preference(
                owner,
                normalizeKey(key),
                input.barcode(),
                input.itemId(),
                input.photoId(),
                input.hidden(),
                input.version()));
    }
}

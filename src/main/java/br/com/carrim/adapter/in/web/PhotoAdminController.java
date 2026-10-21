package br.com.carrim.adapter.in.web;

import br.com.carrim.application.photos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/photo-admin/photos")
public class PhotoAdminController {
    private final PhotoRepository photos;

    public PhotoAdminController(PhotoRepository photos) {
        this.photos = photos;
    }

    public record Decision(
            @NotBlank String decision,
            @NotNull @Min(0) Long version,
            @Size(max = 200) String reason) {}

    @GetMapping
    public ResponseEntity<List<PhotoView>> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(photos.reviewQueue(status, limit, offset));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        return PhotoController.image(photos.content(null, id, true));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<PhotoView> decide(@PathVariable UUID id, @Valid @RequestBody Decision input) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(photos.decide(id, input.decision(), input.version(), input.reason()));
    }
}

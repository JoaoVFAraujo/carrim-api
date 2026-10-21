package br.com.carrim.application.photos;

import java.util.List;
import java.util.UUID;

public interface PhotoRepository {
    void validateTarget(UUID owner, String barcode, UUID itemId);

    PhotoView create(
            UUID owner,
            UUID id,
            String name,
            String barcode,
            UUID itemId,
            String source,
            String inputHash,
            byte[] content,
            String sourceUrl,
            String licenseUrl);

    java.util.Optional<PhotoView> replay(UUID owner, UUID id, String inputHash);

    PhotoView metadata(UUID owner, UUID id);

    byte[] content(UUID owner, UUID id, boolean admin);

    List<PhotoView> mine(UUID owner, int limit, int offset);

    PhotoView approved(String barcode);

    void delete(UUID owner, UUID id);

    List<PhotoPreference> preferences(UUID owner, int limit, int offset);

    PhotoPreference preference(UUID owner, String key);

    PhotoPreference preference(
            UUID owner, String key, String barcode, UUID itemId, UUID photoId, boolean hidden, Long version);

    List<PhotoView> reviewQueue(String status, int limit, int offset);

    PhotoView decide(UUID id, String decision, long version, String reason);
}

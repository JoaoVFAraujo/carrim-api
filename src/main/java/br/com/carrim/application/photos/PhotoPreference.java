package br.com.carrim.application.photos;

import java.util.UUID;

public record PhotoPreference(String key, String barcode, UUID itemId, UUID photoId, boolean hidden, long version) {}

package br.com.carrim.application.identity;

import java.util.UUID;

public record CallerIdentity(UUID userId, UUID installationId, String accountType) {}

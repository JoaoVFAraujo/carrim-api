package br.com.carrim.application.identity;

import java.time.Instant;
import java.util.UUID;

public record IssuedIdentity(
        UUID userId, UUID installationId, String accountType, String accessToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedIdentity[userId=" + userId + ", token=REDACTED]";
    }
}

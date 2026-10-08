package br.com.carrim.application.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

public final class IdentityService {
    private final IdentityRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public IdentityService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public IssuedIdentity bootstrap(BootstrapCommand command) {
        byte[] proof = Base64.getUrlDecoder().decode(command.installationSecret());
        if (proof.length != 32
                || !Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(proof)
                        .equals(command.installationSecret()))
            throw new IllegalArgumentException("Proof must be canonical base64url for 32 bytes");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = "carrim_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = clock.instant();
        var expires = now.plus(Duration.ofDays(30));
        var caller =
                repository.issue(command, hash(proof), hash(token.getBytes(StandardCharsets.US_ASCII)), now, expires);
        return new IssuedIdentity(caller.userId(), caller.installationId(), caller.accountType(), token, expires);
    }

    public Optional<CallerIdentity> authenticate(String token) {
        if (token == null || !token.matches("carrim_[A-Za-z0-9_-]{43}")) return Optional.empty();
        return repository.authenticate(hash(token.getBytes(StandardCharsets.US_ASCII)), clock.instant());
    }

    private static byte[] hash(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

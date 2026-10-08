package br.com.carrim.application.identity;

import java.time.Instant;
import java.util.Optional;

public interface IdentityRepository {
    CallerIdentity issue(BootstrapCommand command, byte[] proofHash, byte[] accessHash, Instant now, Instant expires);

    Optional<CallerIdentity> authenticate(byte[] accessHash, Instant now);
}

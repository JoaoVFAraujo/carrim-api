package br.com.carrim.application.identity;

import java.util.Set;
import java.util.UUID;

public record BootstrapCommand(
        UUID installationId, String installationSecret, String devicePlatform, String appVersion) {
    public BootstrapCommand {
        if (installationId == null || installationSecret == null || !installationSecret.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Installation identity and proof are required");
        if (devicePlatform == null || !Set.of("ANDROID", "IOS", "WEB").contains(devicePlatform))
            throw new IllegalArgumentException("Invalid device platform");
        if (appVersion == null
                || appVersion.length() > 32
                || !appVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?"))
            throw new IllegalArgumentException("Invalid app version");
    }

    @Override
    public String toString() {
        return "BootstrapCommand[installationId=" + installationId + ", proof=REDACTED]";
    }
}

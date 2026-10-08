package br.com.carrim.adapter.in.web;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

final class ApiActor {
    private ApiActor() {}

    static UUID owner(OAuth2AuthenticatedPrincipal principal) {
        if (principal == null || !(principal.getAttribute("user_id") instanceof String id))
            throw new AccessDeniedException("Issued identity required");
        return UUID.fromString(id);
    }
}

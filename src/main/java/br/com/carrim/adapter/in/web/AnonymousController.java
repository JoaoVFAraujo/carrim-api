package br.com.carrim.adapter.in.web;

import br.com.carrim.application.identity.BootstrapCommand;
import br.com.carrim.application.identity.CallerIdentity;
import br.com.carrim.application.identity.IdentityService;
import br.com.carrim.application.identity.IssuedIdentity;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnonymousController {
    private final IdentityService service;

    public AnonymousController(IdentityService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/auth/anonymous", consumes = "application/json")
    public IssuedIdentity bootstrap(@RequestBody BootstrapCommand command) {
        return service.bootstrap(command);
    }

    @GetMapping("/api/v1/auth/me")
    public CallerIdentity me(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        return new CallerIdentity(
                ApiActor.owner(principal),
                UUID.fromString(principal.getAttribute("installation_id")),
                principal.getAttribute("account_type"));
    }
}

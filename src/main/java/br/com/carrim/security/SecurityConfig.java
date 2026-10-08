package br.com.carrim.security;

import br.com.carrim.application.identity.IdentityService;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    OpaqueTokenIntrospector introspector(IdentityService service) {
        return token -> {
            var caller =
                    service.authenticate(token).orElseThrow(() -> new BadOpaqueTokenException("Invalid access token"));
            return new DefaultOAuth2AuthenticatedPrincipal(
                    caller.userId().toString(),
                    Map.of(
                            "user_id",
                            caller.userId().toString(),
                            "installation_id",
                            caller.installationId().toString(),
                            "account_type",
                            caller.accountType()),
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/v1/**")
                .addFilterBefore(new BootstrapRateFilter(), BearerTokenAuthenticationFilter.class)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(BootstrapRateFilter.ROUTE)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                        .access(issuedToken())
                        .requestMatchers(
                                "/api/v1/products/**", "/api/v1/supermarkets/**", "/api/v1/shopping-sessions/**")
                        .access(issuedToken())
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(server -> server.opaqueToken(Customizer.withDefaults()))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> issuedToken() {
        return (authentication, context) ->
                new AuthorizationDecision(authentication.get() instanceof BearerTokenAuthentication
                        && authentication.get().isAuthenticated());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Non-API routes remain closed and retain CSRF protection.
        return http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // Prevent Boot from creating a default user and logging a generated password.
        return username -> {
            throw new UsernameNotFoundException("Authentication is not configured");
        };
    }
}

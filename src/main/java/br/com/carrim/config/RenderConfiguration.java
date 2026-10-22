package br.com.carrim.config;

import java.util.List;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

/** Render terminates TLS; its ingress is the only permitted public entry to this connector. */
@Configuration(proxyBeanMethods = false)
@Profile("prod & render")
public class RenderConfiguration {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> renderHttpsConnector() {
        // Do not process client-supplied forwarded IPs: rate limits retain the socket peer.
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setScheme("https");
            connector.setSecure(true);
            connector.setProxyPort(443);
        });
    }

    @Bean
    CorsConfigurationSource nativeCorsConfigurationSource() {
        var policy = new CorsConfiguration();
        policy.setAllowedOrigins(List.of("https://localhost"));
        policy.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        policy.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        policy.setAllowCredentials(false);
        policy.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        // Native Bearer requests only. Web cookie bootstrap and admin are deliberately absent.
        for (var route : List.of(
                "/api/v1/auth/anonymous",
                "/api/v1/auth/me",
                "/api/v1/products/**",
                "/api/v1/supermarkets/**",
                "/api/v1/shopping-sessions/**",
                "/api/v1/sync/**")) {
            source.registerCorsConfiguration(route, policy);
        }
        return source;
    }

    @Bean
    @Order(-1)
    SecurityFilterChain renderHealth(HttpSecurity http) throws Exception {
        return http.securityMatcher("/healthz")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/healthz")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .build();
    }
}

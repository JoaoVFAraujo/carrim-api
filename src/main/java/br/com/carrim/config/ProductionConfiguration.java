package br.com.carrim.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration {
    @Bean
    String productionDatabasePolicy(@Value("${spring.datasource.url}") String url) {
        validateDatabaseUrl(url);
        return "verified";
    }

    static void validateDatabaseUrl(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://") || !url.contains("?"))
            throw new IllegalStateException("Production requires PostgreSQL with verified TLS.");
        var properties =
                java.util.Arrays.asList(url.substring(url.indexOf('?') + 1).split("&"));
        long modes = properties.stream().filter(p -> p.startsWith("sslmode=")).count();
        if (modes != 1
                || !properties.contains("sslmode=verify-full")
                || properties.stream()
                        .anyMatch(p -> p.startsWith("sslfactory=") || p.startsWith("sslhostnameverifier=")))
            throw new IllegalStateException("Production requires PostgreSQL with verified TLS.");
    }

    @Bean
    @Order(0)
    SecurityFilterChain privateHealth(HttpSecurity http, @Value("${management.server.port:9090}") int port)
            throws Exception {
        return http.securityMatcher("/actuator/health", "/actuator/health/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) -> {
                    var request = context.getRequest();
                    boolean loopback = request.getRemoteAddr().equals("127.0.0.1")
                            || request.getRemoteAddr().equals("0:0:0:0:0:0:0:1")
                            || request.getRemoteAddr().equals("::1");
                    return new AuthorizationDecision(loopback && request.getLocalPort() == port);
                }))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}

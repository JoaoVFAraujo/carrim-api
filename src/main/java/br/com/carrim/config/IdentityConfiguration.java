package br.com.carrim.config;

import br.com.carrim.application.identity.IdentityRepository;
import br.com.carrim.application.identity.IdentityService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {
    @Bean
    IdentityService identityService(IdentityRepository repository) {
        return new IdentityService(repository, Clock.systemUTC());
    }
}

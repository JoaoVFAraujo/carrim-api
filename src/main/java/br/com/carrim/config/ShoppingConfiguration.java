package br.com.carrim.config;

import br.com.carrim.application.shopping.ShoppingOperations;
import br.com.carrim.application.shopping.ShoppingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ShoppingConfiguration {
    @Bean
    ShoppingOperations shoppingOperations(ShoppingRepository repository) {
        return new ShoppingOperations(repository);
    }
}

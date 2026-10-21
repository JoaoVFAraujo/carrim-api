package br.com.carrim.config;

import br.com.carrim.adapter.out.catalog.OpenFoodFactsClient;
import br.com.carrim.application.catalog.CatalogLookup;
import br.com.carrim.application.catalog.CatalogRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class CatalogLookupConfiguration {
    @Bean
    OpenFoodFactsClient openFoodFactsClient(ObjectMapper mapper) {
        return new OpenFoodFactsClient(mapper);
    }

    @Bean
    CatalogLookup catalogLookup(
            CatalogRepository catalog,
            OpenFoodFactsClient external,
            @Value("${carrim.catalog.external-enabled:true}") boolean enabled) {
        return new CatalogLookup(catalog, external, Clock.systemUTC(), enabled);
    }
}

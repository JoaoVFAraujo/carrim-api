package br.com.carrim.config;

import br.com.carrim.adapter.in.web.PhotoRateLimit;
import br.com.carrim.adapter.out.catalog.PhotoExternalImporter;
import br.com.carrim.adapter.out.catalog.PhotoImageCodec;
import br.com.carrim.application.catalog.CatalogLookup;
import br.com.carrim.security.PhotoAdminAccess;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class PhotoConfiguration {
    @Bean
    PhotoImageCodec photoImageCodec() {
        return new PhotoImageCodec();
    }

    @Bean
    PhotoExternalImporter photoExternalImporter(CatalogLookup lookup, PhotoImageCodec codec) {
        return new PhotoExternalImporter(lookup, codec);
    }

    @Bean
    PhotoRateLimit photoRateLimit() {
        return new PhotoRateLimit(Clock.systemUTC());
    }

    @Bean
    PhotoAdminAccess photoAdminAccess(@Value("${CARRIM_PHOTO_ADMIN_TOKEN_SHA256:}") String hash, Environment env) {
        return new PhotoAdminAccess(hash, env);
    }
}

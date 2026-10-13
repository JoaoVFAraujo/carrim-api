package br.com.carrim.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProductionConfigurationTests {
    @Test
    void databaseRequiresVerifiedTls() {
        assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseUrl("jdbc:postgresql://localhost/carrim"));
        assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseUrl(
                        "jdbc:postgresql://localhost/carrim?sslmode=require"));
        assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseUrl(
                        "jdbc:postgresql://localhost/carrim?sslmode=verify-full&sslmode=disable"));
        assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseUrl(
                        "jdbc:postgresql://localhost/carrim?sslmode=verify-full&sslfactory=org.postgresql.ssl.NonValidatingFactory"));
        assertDoesNotThrow(() -> ProductionConfiguration.validateDatabaseUrl(
                "jdbc:postgresql://localhost/carrim?sslmode=verify-full&sslrootcert=/run/db-ca.pem"));
    }
}

package br.com.carrim.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgresTestSupport {
    private static PostgreSQLContainer container;

    @DynamicPropertySource
    static synchronized void databaseProperties(DynamicPropertyRegistry registry) {
        String localUrl = System.getenv("CARRIM_TEST_DATABASE_URL");
        if (localUrl != null) {
            if (!localUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/carrim_test")) {
                throw new IllegalArgumentException("Local tests require an isolated loopback carrim_test database");
            }
            registry.add("spring.datasource.url", () -> localUrl);
            registry.add("spring.datasource.username", () -> "carrim_test");
            registry.add("spring.datasource.password", () -> "");
        } else {
            if (container == null) {
                container = new PostgreSQLContainer("postgres:18").withDatabaseName("carrim_test");
                container.start();
            }
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
    }
}

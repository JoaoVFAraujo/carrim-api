package br.com.carrim.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.carrim.support.PostgresTestSupport;
import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MigrationUpgradeTests extends PostgresTestSupport {
    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    @Test
    void v2PreservesAnExistingV1Catalog() throws Exception {
        String originalUrl;
        try (var connection = dataSource.getConnection()) {
            originalUrl = connection.getMetaData().getURL();
        }
        if (!originalUrl.matches("jdbc:postgresql://[^/]+/carrim_test(?:\\?.*)?"))
            throw new IllegalStateException("Upgrade tests require isolated carrim_test");
        String database = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        String url = originalUrl.replace("/carrim_test", "/" + database);
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        var first = Flyway.configure()
                .dataSource(url, username, password)
                .schemas("carrim")
                .defaultSchema("carrim")
                .target("1")
                .load();
        first.migrate();
        UUID owner = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO carrim.users(id,account_type) VALUES ('" + owner + "','ANONYMOUS')");
            statement.execute("INSERT INTO carrim.products(id,user_id,name,barcode,measurement_type) VALUES ('"
                    + product + "','" + owner + "','Original','0789600112233','UNIT')");
        }
        var second = Flyway.configure()
                .dataSource(url, username, password)
                .schemas("carrim")
                .defaultSchema("carrim")
                .target("2")
                .load();
        assertEquals(1, second.migrate().migrationsExecuted);
        assertEquals("2", second.info().current().getVersion().getVersion());
        assertEquals(0, second.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT name,barcode,version FROM carrim.products WHERE id='" + product + "'")) {
            assertTrue(result.next());
            assertEquals("Original", result.getString(1));
            assertEquals("0789600112233", result.getString(2));
            assertEquals(0, result.getLong(3));
        }
    }

    @Test
    void v7PreservesExistingV6BusinessDataAndDoesNotBackfillJournal() throws Exception {
        String originalUrl;
        try (var connection = dataSource.getConnection()) {
            originalUrl = connection.getMetaData().getURL();
        }
        if (!originalUrl.matches("jdbc:postgresql://[^/]+/carrim_test(?:\\?.*)?"))
            throw new IllegalStateException("Isolated carrim_test required");
        String database = "photos_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        String url = originalUrl.replace("/carrim_test", "/" + database);
        String user = environment.getProperty("spring.datasource.username"),
                password = environment.getProperty("spring.datasource.password");
        Flyway.configure()
                .dataSource(url, user, password)
                .schemas("carrim")
                .defaultSchema("carrim")
                .target("6")
                .load()
                .migrate();
        UUID owner = UUID.randomUUID(), product = UUID.randomUUID();
        long journal;
        try (var connection = DriverManager.getConnection(url, user, password)) {
            try (var insert =
                    connection.prepareStatement("INSERT INTO carrim.users(id,account_type) VALUES (?,'ANONYMOUS')")) {
                insert.setObject(1, owner);
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO carrim.products(id,user_id,name,barcode,measurement_type) VALUES (?,?,'Original photo-less product','0789600112233','UNIT')")) {
                insert.setObject(1, product);
                insert.setObject(2, owner);
                insert.executeUpdate();
            }
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM carrim.sync_changes")) {
                result.next();
                journal = result.getLong(1);
            }
        }
        var upgrade = Flyway.configure()
                .dataSource(url, user, password)
                .schemas("carrim")
                .defaultSchema("carrim")
                .target("7")
                .load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        assertEquals(0, upgrade.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT name,barcode,version FROM carrim.products")) {
                assertTrue(result.next());
                assertEquals("Original photo-less product", result.getString(1));
                assertEquals("0789600112233", result.getString(2));
                assertEquals(0, result.getLong(3));
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM carrim.product_photos")) {
                result.next();
                assertEquals(0, result.getLong(1));
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM carrim.sync_changes")) {
                result.next();
                assertEquals(journal, result.getLong(1));
            }
        }
    }
}

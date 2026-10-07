package br.com.carrim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.carrim.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CarrimApplicationTests extends PostgresTestSupport {

    @Autowired
    private Flyway flyway;

    @Test
    void contextLoadsWithMigratedPostgres() {
        assertEquals("1", flyway.info().current().getVersion().getVersion());
    }
}

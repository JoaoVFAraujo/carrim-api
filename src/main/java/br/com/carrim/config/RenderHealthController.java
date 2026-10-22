package br.com.carrim.config;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("prod & render")
public class RenderHealthController {
    private final DataSource dataSource;

    public RenderHealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> health() {
        boolean up;
        try (var connection = dataSource.getConnection()) {
            up = connection.isValid(1);
        } catch (SQLException error) {
            up = false;
        }
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", up ? "UP" : "DOWN"));
    }
}

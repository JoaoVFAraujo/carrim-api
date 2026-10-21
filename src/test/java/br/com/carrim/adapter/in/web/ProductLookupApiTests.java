package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.carrim.adapter.out.catalog.OpenFoodFactsClient;
import br.com.carrim.application.catalog.CatalogRepository;
import br.com.carrim.application.catalog.ExternalProductCatalog;
import br.com.carrim.application.catalog.ProductLookup;
import br.com.carrim.application.identity.BootstrapCommand;
import br.com.carrim.application.identity.IdentityService;
import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.support.PostgresTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProductLookupApiTests extends PostgresTestSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityService identities;

    @Autowired
    CatalogRepository catalog;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    OpenFoodFactsClient external;

    @Test
    void requiresBearerAndRejectsInvalidBarcodeBeforeProvider() throws Exception {
        mvc.perform(get("/api/v1/products/lookup/7891000100103")).andExpect(status().isUnauthorized());
        var actor = identities.bootstrap(
                new BootstrapCommand(UUID.randomUUID(), AnonymousIdentityTests.proof(), "WEB", "0.0.2"));
        mvc.perform(get("/api/v1/products/lookup/not-a-barcode")
                        .header("Authorization", "Bearer " + actor.accessToken()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(external);
    }

    @Test
    void doesNotLeakAnotherOwnersCatalogOrWriteProductsAndJournal() throws Exception {
        String code = "0789600112233";
        var owner = identities.bootstrap(
                new BootstrapCommand(UUID.randomUUID(), AnonymousIdentityTests.proof(), "WEB", "0.0.2"));
        var other = identities.bootstrap(
                new BootstrapCommand(UUID.randomUUID(), AnonymousIdentityTests.proof(), "WEB", "0.0.2"));
        catalog.createProduct(
                owner.userId(), new Product(UUID.randomUUID(), "Personal correction", code, MeasurementType.UNIT));
        when(external.lookup(code))
                .thenReturn(new ExternalProductCatalog.Reply(
                        new ProductLookup(
                                ProductLookup.Status.FOUND,
                                ProductLookup.Source.OPEN_FOOD_FACTS,
                                code,
                                "Public suggestion",
                                "Brand",
                                "500 g",
                                null,
                                "https://world.openfoodfacts.org/product/" + code,
                                "https://opendatacommons.org/licenses/odbl/1-0/",
                                null),
                        0));
        int products = jdbc.queryForObject("select count(*) from carrim.products", Integer.class);
        int changes = jdbc.queryForObject("select count(*) from carrim.sync_changes", Integer.class);
        mvc.perform(get("/api/v1/products/lookup/" + code).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("FOUND"))
                .andExpect(jsonPath("$.source").value("PERSONAL"))
                .andExpect(jsonPath("$.name").value("Personal correction"));
        verifyNoInteractions(external);
        mvc.perform(get("/api/v1/products/lookup/" + code).header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("OPEN_FOOD_FACTS"))
                .andExpect(jsonPath("$.name").value("Public suggestion"));
        assertEquals(products, jdbc.queryForObject("select count(*) from carrim.products", Integer.class));
        assertEquals(changes, jdbc.queryForObject("select count(*) from carrim.sync_changes", Integer.class));
    }
}

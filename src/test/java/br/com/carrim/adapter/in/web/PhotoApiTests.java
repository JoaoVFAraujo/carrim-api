package br.com.carrim.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.carrim.adapter.out.catalog.PhotoExternalImporter;
import br.com.carrim.adapter.out.persistence.JdbcPhotoRepository;
import br.com.carrim.application.catalog.CatalogRepository;
import br.com.carrim.application.identity.*;
import br.com.carrim.application.photos.PhotoRepository;
import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.support.PostgresTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "carrim.catalog.external-enabled=false")
@AutoConfigureMockMvc
class PhotoApiTests extends PostgresTestSupport {
    static final String ADMIN = "A".repeat(43);
    static final String CODE = "7898080643179";

    @DynamicPropertySource
    static void admin(DynamicPropertyRegistry registry) {
        registry.add(
                "CARRIM_PHOTO_ADMIN_TOKEN_SHA256",
                () -> JdbcPhotoRepository.hash(ADMIN.getBytes(StandardCharsets.US_ASCII)));
    }

    @MockitoBean
    PhotoExternalImporter external;

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityService identities;

    @Autowired
    CatalogRepository catalog;

    @Autowired
    PhotoRepository photos;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    IssuedIdentity actor() {
        return identities.bootstrap(
                new BootstrapCommand(UUID.randomUUID(), AnonymousIdentityTests.proof(), "WEB", "0.0.2"));
    }

    void product(IssuedIdentity owner) {
        catalog.createProduct(owner.userId(), new Product(UUID.randomUUID(), "Aveia", CODE, MeasurementType.UNIT));
    }

    byte[] raster() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    String uploadBody(String code, UUID itemId) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("name", "Aveia");
        body.put("barcode", code);
        body.put("itemId", itemId);
        body.put("contentType", "image/png");
        body.put("imageBase64", Base64.getEncoder().encodeToString(raster()));
        return json.writeValueAsString(body);
    }

    void upload(IssuedIdentity actor, UUID photoId) throws Exception {
        mvc.perform(put("/api/v1/products/photos/" + photoId)
                        .header("Authorization", "Bearer " + actor.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(CODE, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.own").value(true));
    }

    @Test
    void photoIsPrivateUntilApprovalAndReplacementWaitsForReview() throws Exception {
        var owner = actor();
        var other = actor();
        product(owner);
        UUID id = UUID.randomUUID();
        long journal = jdbc.queryForObject("SELECT count(*) FROM carrim.sync_changes", Long.class);
        upload(owner, id);
        bind(owner, id, null);
        mvc.perform(get("/api/v1/products/lookup/" + CODE).header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").isEmpty());
        assertEquals(journal, jdbc.queryForObject("SELECT count(*) FROM carrim.sync_changes", Long.class));
        mvc.perform(get("/api/v1/products/photos/" + id + "/content")
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/photos/approved/" + CODE)
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/photo-admin/photos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/photo-admin/photos").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/photo-admin/photos/" + id + "/decision")
                        .header("X-Carrim-Photo-Admin", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/v1/products/photos/approved/" + CODE)
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.own").value(false))
                .andExpect(jsonPath("$.itemId").isEmpty());
        mvc.perform(get("/api/v1/products/photos/" + id + "/content")
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/products/lookup/" + CODE).header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("CARRIM_APPROVED"))
                .andExpect(jsonPath("$.name").value("Aveia"))
                .andExpect(jsonPath("$.imageUrl").isEmpty());
        UUID pending = UUID.randomUUID();
        upload(owner, pending);
        mvc.perform(get("/api/v1/products/photos/approved/" + CODE)
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(post("/api/v1/photo-admin/photos/" + pending + "/decision")
                        .header("X-Carrim-Photo-Admin", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"version\":0,\"reason\":\"Imagem incorreta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/products/photos/" + pending).header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM carrim.photo_review_audit WHERE photo_id IN (?,?)",
                        Integer.class,
                        id,
                        pending));
    }

    void bind(IssuedIdentity owner, UUID photo, Long version) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("barcode", CODE);
        body.put("photoId", photo);
        body.put("hidden", false);
        body.put("version", version);
        mvc.perform(put("/api/v1/products/photos/preferences/barcode-" + CODE)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void supersededPendingCannotBeApprovedAndApprovedPhotoSurvivesNewPending() throws Exception {
        var owner = actor();
        String code = "7898080643186";
        catalog.createProduct(owner.userId(), new Product(UUID.randomUUID(), "Aveia", code, MeasurementType.UNIT));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        byte[] image = new br.com.carrim.adapter.out.catalog.PhotoImageCodec().normalize(raster(), "image/png");
        photos.create(owner.userId(), a, "Aveia", code, null, "USER", a.toString(), image, null, null);
        photos.preference(owner.userId(), "barcode:" + code, code, null, a, false, null);
        photos.create(owner.userId(), b, "Aveia", code, null, "USER", b.toString(), image, null, null);
        photos.preference(owner.userId(), "barcode:" + code, code, null, b, false, 0L);
        assertEquals("DELETED", photos.metadata(owner.userId(), a).status());
        assertNull(jdbc.queryForObject("SELECT content FROM carrim.product_photos WHERE id=?", byte[].class, a));
        mvc.perform(post("/api/v1/photo-admin/photos/" + a + "/decision")
                        .header("X-Carrim-Photo-Admin", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/photo-admin/photos/" + b + "/decision")
                        .header("X-Carrim-Photo-Admin", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"version\":0}"))
                .andExpect(status().isOk());
        photos.create(owner.userId(), c, "Aveia", code, null, "USER", c.toString(), image, null, null);
        photos.preference(owner.userId(), "barcode:" + code, code, null, c, false, 1L);
        assertEquals(b, photos.approved(code).id());
        assertEquals("APPROVED", photos.metadata(owner.userId(), b).status());
        mvc.perform(post("/api/v1/photo-admin/photos/" + c + "/decision")
                        .header("X-Carrim-Photo-Admin", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"version\":0}"))
                .andExpect(status().isOk());
        assertEquals(c, photos.approved(code).id());
    }

    @Test
    void deletedAndSupersededPhotosCannotReplaceTheCurrentPendingPreference() throws Exception {
        var owner = actor();
        product(owner);
        UUID superseded = UUID.randomUUID(), current = UUID.randomUUID(), deleted = UUID.randomUUID();
        upload(owner, superseded);
        bind(owner, superseded, null);
        upload(owner, current);
        bind(owner, current, 0L);
        upload(owner, deleted);
        photos.delete(owner.userId(), deleted);
        assertEquals("DELETED", photos.metadata(owner.userId(), superseded).status());
        assertEquals("DELETED", photos.metadata(owner.userId(), deleted).status());
        byte[] currentContent = photos.content(owner.userId(), current, false);
        var currentPreference = photos.preference(owner.userId(), "barcode:" + CODE);
        long audit = jdbc.queryForObject("SELECT count(*) FROM carrim.photo_review_audit", Long.class);
        for (UUID tombstone : List.of(superseded, deleted)) {
            var body = Map.of("barcode", CODE, "photoId", tombstone, "hidden", false, "version", 1);
            mvc.perform(put("/api/v1/products/photos/preferences/barcode-" + CODE)
                            .header("Authorization", "Bearer " + owner.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isNotFound());
            assertEquals(currentPreference, photos.preference(owner.userId(), "barcode:" + CODE));
            assertEquals("PENDING", photos.metadata(owner.userId(), current).status());
            assertEquals(0, photos.metadata(owner.userId(), current).version());
            assertArrayEquals(currentContent, photos.content(owner.userId(), current, false));
            assertEquals(audit, jdbc.queryForObject("SELECT count(*) FROM carrim.photo_review_audit", Long.class));
        }
    }

    @Test
    void unboundCandidateCannotBeApproved() throws Exception {
        var owner = actor();
        product(owner);
        UUID id = UUID.randomUUID();
        upload(owner, id);
        mvc.perform(post("/api/v1/photo-admin/photos/" + id + "/decision")
                        .header("X-Carrim-Photo-Admin", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"version\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void externalImportChecksOwnershipBeforeSourceLookup() throws Exception {
        var owner = actor();
        mvc.perform(post("/api/v1/products/photos/" + UUID.randomUUID() + "/external")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aveia\",\"barcode\":\"" + CODE + "\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(external);
    }

    @Test
    void replayAndDeleteAreIdempotentAndDoNotResurrect() throws Exception {
        var owner = actor();
        product(owner);
        UUID id = UUID.randomUUID();
        upload(owner, id);
        upload(owner, id);
        mvc.perform(put("/api/v1/products/photos/" + id)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(CODE, null).replace("Aveia", "Changed")))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/products/photos/" + id).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete("/api/v1/products/photos/" + id).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/products/photos/" + id)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(CODE, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));
        assertNull(jdbc.queryForObject("SELECT content FROM carrim.product_photos WHERE id=?", byte[].class, id));
        mvc.perform(get("/api/v1/products/photos/" + id + "/content")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownershipPreferencesAndMalformedImagesAreEnforced() throws Exception {
        var owner = actor();
        var other = actor();
        product(owner);
        UUID id = UUID.randomUUID();
        upload(owner, id);
        mvc.perform(get("/api/v1/products/photos/mine").header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(delete("/api/v1/products/photos/" + id).header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());
        String pref = "{\"barcode\":\"" + CODE + "\",\"photoId\":\"" + id + "\",\"hidden\":false,\"version\":null}";
        mvc.perform(put("/api/v1/products/photos/preferences/barcode-" + CODE)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("barcode:" + CODE))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(put("/api/v1/products/photos/preferences/barcode-" + CODE)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pref))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/products/photos/preferences/barcode-" + CODE)
                        .header("Authorization", "Bearer " + other.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pref))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/products/photos/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(CODE, null).replace("image/png", "image/jpeg")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/products/photos/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(CODE, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manualPhotoCanUploadAfterPurchaseClosedWithoutChangingItemOrBusinessJournal() throws Exception {
        var owner = actor();
        UUID market = UUID.randomUUID(),
                session = UUID.randomUUID(),
                item = UUID.randomUUID(),
                photo = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO carrim.supermarkets(id,user_id,name) VALUES (?,?,?)", market, owner.userId(), "Market");
        jdbc.update(
                "INSERT INTO carrim.shopping_sessions(id,user_id,supermarket_id,status,started_at) VALUES (?,?,?,'ACTIVE',clock_timestamp())",
                session,
                owner.userId(),
                market);
        jdbc.update(
                "INSERT INTO carrim.shopping_items(id,user_id,session_id,product_name_snapshot,measurement_type,pricing_type,reference_price_cents,quantity,position) VALUES (?,?,?,'Manual','UNIT','REGULAR',100,1,0)",
                item,
                owner.userId(),
                session);
        jdbc.update(
                "UPDATE carrim.shopping_sessions SET status='CANCELED',finished_at=clock_timestamp(),version=version+1 WHERE id=?",
                session);
        long journal = jdbc.queryForObject("SELECT count(*) FROM carrim.sync_changes", Long.class);
        mvc.perform(put("/api/v1/products/photos/" + photo)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(null, item)))
                .andExpect(status().isOk());
        String pref = json.writeValueAsString(Map.of("itemId", item, "photoId", photo, "hidden", false));
        mvc.perform(put("/api/v1/products/photos/preferences/item-" + item)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pref))
                .andExpect(status().isOk());
        assertEquals(
                100,
                jdbc.queryForObject(
                        "SELECT reference_price_cents FROM carrim.shopping_items WHERE id=?", Integer.class, item));
        assertEquals(journal, jdbc.queryForObject("SELECT count(*) FROM carrim.sync_changes", Long.class));
    }

    @Test
    void readsExactPreferenceOnlyForItsOwner() throws Exception {
        var owner = actor();
        var other = actor();
        product(owner);
        mvc.perform(put("/api/v1/products/photos/preferences/barcode-" + CODE)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"" + CODE
                                + "\",\"itemId\":null,\"photoId\":null,\"hidden\":true,\"version\":null}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/photos/preferences/barcode-" + CODE)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("barcode:" + CODE))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.hidden").value(true));
        mvc.perform(get("/api/v1/products/photos/preferences/barcode-" + CODE)
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());
    }
}

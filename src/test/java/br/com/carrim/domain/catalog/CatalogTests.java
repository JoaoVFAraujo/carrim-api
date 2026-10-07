package br.com.carrim.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.carrim.domain.shopping.MeasurementType;
import br.com.carrim.domain.supermarket.Supermarket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogTests {
    @Test
    void preservesLeadingZerosAndAllowsManualProducts() {
        Product coded = new Product(UUID.randomUUID(), " Café ", " 0789600112233 ", MeasurementType.UNIT);
        assertEquals("Café", coded.name());
        assertEquals("0789600112233", coded.barcode());
        assertNull(new Product(UUID.randomUUID(), "Banana", null, MeasurementType.WEIGHT).barcode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "012345678901", "0789600112233"})
    void acceptsRetailCodeLengths(String code) {
        assertEquals(code, new Product(UUID.randomUUID(), "Product", code, MeasurementType.UNIT).barcode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567", "123456789", "12345678901234", "078960011223a", "１２３４５６７８", "1234 5678"})
    void rejectsInvalidCodesInsteadOfCoercingThem(String code) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Product(UUID.randomUUID(), "Product", code, MeasurementType.UNIT));
    }

    @Test
    void renamePreservesIdentityAndOriginalCatalogValue() {
        Product original = new Product(UUID.randomUUID(), "Coffee", "12345678", MeasurementType.UNIT);
        Product renamed = original.rename("Café");
        assertEquals(original.id(), renamed.id());
        assertEquals(original.barcode(), renamed.barcode());
        assertEquals("Coffee", original.name());
        Supermarket market = new Supermarket(UUID.randomUUID(), " São Luiz ");
        assertEquals("São Luiz", market.name());
        assertEquals(market.id(), market.rename("Novo nome").id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsEmptyNames(String name) {
        assertThrows(
                IllegalArgumentException.class, () -> new Product(UUID.randomUUID(), name, null, MeasurementType.UNIT));
        assertThrows(IllegalArgumentException.class, () -> new Supermarket(UUID.randomUUID(), name));
    }

    @Test
    void rejectsMissingIdentitiesAndOverlongNames() {
        assertThrows(NullPointerException.class, () -> new Supermarket(null, "Market"));
        assertThrows(NullPointerException.class, () -> new Product(null, "Coffee", null, MeasurementType.UNIT));
        assertThrows(NullPointerException.class, () -> new Product(UUID.randomUUID(), "Coffee", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Supermarket(UUID.randomUUID(), "a".repeat(121)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Product(UUID.randomUUID(), "a".repeat(121), null, MeasurementType.UNIT));
    }
}

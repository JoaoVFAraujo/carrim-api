package br.com.carrim.adapter.out.persistence;

import br.com.carrim.domain.catalog.Product;
import br.com.carrim.domain.shopping.MeasurementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "products")
class ProductEntity {
    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID ownerId;

    @Column(nullable = false, length = 120)
    String name;

    @Column(length = 13)
    String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_type", nullable = false, length = 8)
    MeasurementType measurementType;

    @Version
    Long version;

    protected ProductEntity() {}

    ProductEntity(UUID ownerId, Product product) {
        this.id = product.id();
        this.ownerId = ownerId;
        apply(product);
    }

    void apply(Product product) {
        name = product.name();
        barcode = product.barcode();
        measurementType = product.measurementType();
    }

    Product toDomain() {
        return new Product(id, name, barcode, measurementType);
    }
}

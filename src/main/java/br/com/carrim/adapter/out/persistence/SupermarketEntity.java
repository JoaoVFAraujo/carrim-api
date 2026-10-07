package br.com.carrim.adapter.out.persistence;

import br.com.carrim.domain.supermarket.Supermarket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "supermarkets")
class SupermarketEntity {
    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID ownerId;

    @Column(nullable = false, length = 120)
    String name;

    @Version
    Long version;

    protected SupermarketEntity() {}

    SupermarketEntity(UUID ownerId, Supermarket supermarket) {
        id = supermarket.id();
        this.ownerId = ownerId;
        name = supermarket.name();
    }

    Supermarket toDomain() {
        return new Supermarket(id, name);
    }
}

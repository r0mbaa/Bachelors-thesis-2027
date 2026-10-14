package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.topology.Location;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Запись журнала движений (FR-M4-03). Только вставка: UPDATE и DELETE отвергает триггер БД. */
@Entity
@Immutable
@Table(name = "movement")
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private MovementType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_from")
    private Location locationFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_to")
    private Location locationTo;

    private String documentType;

    private String documentId;

    private String username;

    private Instant occurredAt;

    private String comment;

    protected Movement() {
    }

    Movement(MovementType type, Sku sku, int quantity, Location from, Location to, DocumentRef document,
            String username, Instant occurredAt, String comment) {
        this.type = type;
        this.sku = sku;
        this.quantity = quantity;
        this.locationFrom = from;
        this.locationTo = to;
        this.documentType = document.type();
        this.documentId = document.id();
        this.username = username;
        this.occurredAt = occurredAt;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public MovementType getType() {
        return type;
    }

    public Sku getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Location getLocationFrom() {
        return locationFrom;
    }

    public Location getLocationTo() {
        return locationTo;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getComment() {
        return comment;
    }
}

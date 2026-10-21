package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.topology.Warehouse;
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
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Волна: заказы, планируемые совместно (FR-M5-05). */
@Entity
@Table(name = "wave")
public class Wave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private String number;

    @Enumerated(EnumType.STRING)
    private WaveStatus status = WaveStatus.FORMED;

    @JdbcTypeCode(SqlTypes.JSON)
    private String criteria;

    private String createdBy;

    private Instant createdAt;

    @Version
    private long version;

    protected Wave() {
    }

    Wave(Warehouse warehouse, String number, String criteria, String createdBy, Instant createdAt) {
        this.warehouse = warehouse;
        this.number = number;
        this.criteria = criteria;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getNumber() {
        return number;
    }

    public WaveStatus getStatus() {
        return status;
    }

    public void setStatus(WaveStatus status) {
        this.status = status;
    }

    public String getCriteria() {
        return criteria;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Сохранённая версия планировки. Версии не меняются и не удаляются. */
@Entity
@Immutable
@Table(name = "layout_version")
class LayoutVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private long version;

    /** JSON документа {@code Layout} из библиотеки {@code layout}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String document;

    private String createdBy;

    private Instant createdAt;

    protected LayoutVersion() {
    }

    LayoutVersion(Warehouse warehouse, long version, String document, String createdBy, Instant createdAt) {
        this.warehouse = warehouse;
        this.version = version;
        this.document = document;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    String getDocument() {
        return document;
    }
}

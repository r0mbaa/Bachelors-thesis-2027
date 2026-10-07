package io.github.r0mbaa.wms.core.topology;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Склад (FR-M1-01). Код склада — первый компонент адреса каждой ячейки, поэтому после создания
 * не меняется.
 */
@Entity
@Table(name = "warehouse")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    /** Скорость сборщика по умолчанию, м/с: переводит длину маршрута во время (§8.1). */
    private double defaultSpeedMps;

    private long layoutVersion;

    private Instant createdAt;

    @Version
    private long version;

    protected Warehouse() {
    }

    public Warehouse(String code, String name, double defaultSpeedMps, Instant createdAt) {
        this.code = code;
        this.name = name;
        this.defaultSpeedMps = defaultSpeedMps;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public double getDefaultSpeedMps() {
        return defaultSpeedMps;
    }

    public long getLayoutVersion() {
        return layoutVersion;
    }

    public void setLayoutVersion(long layoutVersion) {
        this.layoutVersion = layoutVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Место, куда поступает принятый товар до размещения (§6.2, шаг 3). */
    public String receivingCode() {
        return code + "-RECEIVING";
    }

    /** Место консолидации собранного перед отгрузкой. */
    public String shippingCode() {
        return code + "-SHIPPING";
    }
}

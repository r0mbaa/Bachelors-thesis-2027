package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Тара или тележка сборщика (FR-M13-04). Вместимость ограничивает батч (FR-M7-02), а своё
 * виртуальное место хранения держит отобранное до отгрузки (FR-M10-11).
 */
@Entity
@Table(name = "container")
public class Container {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private String code;

    @Enumerated(EnumType.STRING)
    private ContainerType type;

    @Column(name = "max_volume_m3")
    private double maxVolumeM3;

    @Column(name = "max_weight_kg")
    private double maxWeightKg;

    private int slots;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    private boolean active = true;

    @Version
    private long version;

    protected Container() {
    }

    Container(Warehouse warehouse, String code, ContainerType type, double maxVolumeM3, double maxWeightKg, int slots,
            Location location) {
        if (!(maxVolumeM3 > 0) || !(maxWeightKg > 0) || slots < 1) {
            throw new IllegalArgumentException("Вместимость тары " + code + " должна быть положительной: объём, вес "
                    + "и хотя бы одно отделение");
        }
        this.warehouse = warehouse;
        this.code = new QrPayload.Container(code).code();
        this.type = type;
        this.maxVolumeM3 = maxVolumeM3;
        this.maxWeightKg = maxWeightKg;
        this.slots = slots;
        this.location = location;
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getCode() {
        return code;
    }

    public ContainerType getType() {
        return type;
    }

    public double getMaxVolumeM3() {
        return maxVolumeM3;
    }

    public double getMaxWeightKg() {
        return maxWeightKg;
    }

    public int getSlots() {
        return slots;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public String qrPayload() {
        return new QrPayload.Container(code).encode();
    }

    /** Код виртуального места тары: {@code WH1-CNT-T07}. */
    static String locationCode(Warehouse warehouse, String containerCode) {
        return warehouse.getCode() + "-CNT-" + containerCode;
    }
}

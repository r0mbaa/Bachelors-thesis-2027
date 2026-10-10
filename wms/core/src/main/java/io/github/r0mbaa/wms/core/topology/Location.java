package io.github.r0mbaa.wms.core.topology;

import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.EnumSet;
import java.util.Set;

/**
 * Место хранения. Ячейки стеллажей создаёт и обновляет только материализация планировки
 * ({@code core.layout}); через JPA у них меняются лишь зона, тип и блокировка. Виртуальные
 * места (приёмка, отгрузка, тара) создаются вместе со складом или тарой.
 */
@Entity
@Table(name = "location")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    private String code;

    @Enumerated(EnumType.STRING)
    private LocationType type;

    @Column(name = "row_no")
    private Integer rowNo;

    @Column(name = "section_no")
    private Integer sectionNo;

    @Column(name = "level_no")
    private Integer levelNo;

    @Column(name = "position_no")
    private Integer positionNo;

    private Double x;

    private Double y;

    private Double z;

    @Column(name = "access_x")
    private Double accessX;

    @Column(name = "access_y")
    private Double accessY;

    @Enumerated(EnumType.STRING)
    private Facing facing;

    @Column(name = "max_weight_kg")
    private Double maxWeightKg;

    @Column(name = "max_volume_m3")
    private Double maxVolumeM3;

    /** Пусто — ячейка принимает товар любого класса хранения. */
    @Convert(converter = StorageClassesConverter.class)
    @Column(name = "allowed_storage_classes")
    private Set<StorageClass> allowedStorageClasses = Set.of();

    private boolean blocked;

    private String blockReason;

    private boolean active = true;

    private Long layoutVersion;

    @Version
    private long version;

    protected Location() {
    }

    /** Виртуальное место без адреса на стеллаже. */
    public static Location virtual(Warehouse warehouse, String code, LocationType type) {
        if (type.isStorage()) {
            throw new IllegalArgumentException("Тип " + type + " относится к ячейкам стеллажей, а не к виртуальным местам");
        }
        Location location = new Location();
        location.warehouse = warehouse;
        location.code = code;
        location.type = type;
        return location;
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Zone getZone() {
        return zone;
    }

    public String getCode() {
        return code;
    }

    public LocationType getType() {
        return type;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public Integer getSectionNo() {
        return sectionNo;
    }

    public Integer getLevelNo() {
        return levelNo;
    }

    public Integer getPositionNo() {
        return positionNo;
    }

    public Double getX() {
        return x;
    }

    public Double getY() {
        return y;
    }

    public Double getZ() {
        return z;
    }

    public Double getAccessX() {
        return accessX;
    }

    public Double getAccessY() {
        return accessY;
    }

    public Facing getFacing() {
        return facing;
    }

    public Double getMaxWeightKg() {
        return maxWeightKg;
    }

    public Double getMaxVolumeM3() {
        return maxVolumeM3;
    }

    public Set<StorageClass> getAllowedStorageClasses() {
        return allowedStorageClasses;
    }

    /** Совместимость класса хранения товара с ячейкой (FR-M1-06, FR-M2-04). */
    public boolean accepts(StorageClass storageClass) {
        return allowedStorageClasses.isEmpty() || allowedStorageClasses.contains(storageClass);
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public boolean isActive() {
        return active;
    }

    /** Ячейка стеллажа, выведенная из планировки, в отличие от виртуального места. */
    public boolean isCell() {
        return rowNo != null;
    }

    /** Нагрузка QR-этикетки; у виртуальных мест этикетки нет. */
    public String qrPayload() {
        return isCell() ? new QrPayload.Location(LocationCode.parse(code)).encode() : null;
    }

    void block(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Укажите причину блокировки ячейки: её увидят кладовщик и диспетчер");
        }
        this.blocked = true;
        this.blockReason = reason.strip();
    }

    void unblock() {
        this.blocked = false;
        this.blockReason = null;
    }

    void setType(LocationType type) {
        this.type = type;
    }

    void setAllowedStorageClasses(Set<StorageClass> classes) {
        this.allowedStorageClasses = classes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(classes));
    }
}

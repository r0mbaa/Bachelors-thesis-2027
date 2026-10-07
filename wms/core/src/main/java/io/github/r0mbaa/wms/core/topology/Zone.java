package io.github.r0mbaa.wms.core.topology;

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

@Entity
@Table(name = "zone")
public class Zone {

    /** Зона, которую получает склад при создании: в неё попадают ячейки первой планировки. */
    public static final String DEFAULT_CODE = "MAIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private LocationType type;

    @Enumerated(EnumType.STRING)
    private StoragePolicy storagePolicy;

    protected Zone() {
    }

    public Zone(Warehouse warehouse, String code, String name, LocationType type, StoragePolicy storagePolicy) {
        if (!type.isStorage()) {
            throw new IllegalArgumentException("Тип зоны " + type
                    + " недопустим: зона объединяет ячейки стеллажей (PICKING, BULK, BUFFER, QUARANTINE, DEFECT)");
        }
        this.warehouse = warehouse;
        this.code = code;
        this.name = name;
        this.type = type;
        this.storagePolicy = storagePolicy;
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

    public String getName() {
        return name;
    }

    public LocationType getType() {
        return type;
    }

    public StoragePolicy getStoragePolicy() {
        return storagePolicy;
    }
}

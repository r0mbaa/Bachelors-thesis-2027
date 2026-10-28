package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.admin.AppUser;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
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
 * Сборщик в реестре (FR-M9-01): учётная запись, код бейджа, статус и последняя подтверждённая
 * ячейка, от которой диспетчеризация считает холостой пробег до следующего задания (§8.5).
 */
@Entity
@Table(name = "worker")
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    private String code;

    private String pinHash;

    @Enumerated(EnumType.STRING)
    private WorkerStatus status = WorkerStatus.OFF_SHIFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_location_id")
    private Location currentLocation;

    @Version
    private long version;

    protected Worker() {
    }

    Worker(Warehouse warehouse, AppUser user, String code, String pinHash) {
        this.warehouse = warehouse;
        this.user = user;
        this.code = new QrPayload.Worker(code).code();
        this.pinHash = pinHash;
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public AppUser getUser() {
        return user;
    }

    public String getCode() {
        return code;
    }

    String getPinHash() {
        return pinHash;
    }

    void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    void setStatus(WorkerStatus status) {
        this.status = status;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    void setCurrentLocation(Location location) {
        this.currentLocation = location;
    }

    public boolean isOnShift() {
        return status != WorkerStatus.OFF_SHIFT;
    }
}

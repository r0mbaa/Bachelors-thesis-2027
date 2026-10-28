package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.LocationType;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Справочник тары и тележек (FR-M13-04). */
@Service
public class ContainerService {

    private static final int MAX_CODE_LENGTH = 20;

    private final ContainerRepository containers;
    private final TopologyService topology;
    private final AuditLog audit;

    ContainerService(ContainerRepository containers, TopologyService topology, AuditLog audit) {
        this.containers = containers;
        this.topology = topology;
        this.audit = audit;
    }

    @Transactional
    public Container create(String warehouseCode, String code, ContainerType type, double maxVolumeM3,
            double maxWeightKg, int slots) {
        Warehouse warehouse = topology.warehouse(warehouseCode);
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Код тары должен содержать от 1 до " + MAX_CODE_LENGTH + " символов");
        }
        if (containers.existsByCode(normalized)) {
            throw new ConflictException("Тара с кодом " + normalized + " уже есть");
        }
        Location location = topology.createVirtualLocation(warehouse, Container.locationCode(warehouse, normalized),
                LocationType.CONTAINER);
        Container container = containers.save(new Container(warehouse, normalized, type, maxVolumeM3, maxWeightKg,
                slots, location));
        audit.record("CONTAINER_CREATED", "CONTAINER", normalized, null,
                Map.of("type", type, "maxVolumeM3", maxVolumeM3, "maxWeightKg", maxWeightKg, "slots", slots));
        return container;
    }

    @Transactional(readOnly = true)
    public List<Container> list(String warehouseCode) {
        return containers.findByWarehouseOrderByCode(topology.warehouse(warehouseCode));
    }

    /** Тара по коду или по скану QR {@code CNT:…}. */
    @Transactional(readOnly = true)
    public Container get(String scanned) {
        return containers.findByCode(codeOf(scanned))
                .orElseThrow(() -> new NotFoundException("Тара " + scanned + " не найдена: отсканируйте QR на тележке"));
    }

    @Transactional
    public Container setActive(String code, boolean active) {
        Container container = get(code);
        container.setActive(active);
        audit.record(active ? "CONTAINER_ENABLED" : "CONTAINER_DISABLED", "CONTAINER", container.getCode(), null, null);
        return container;
    }

    static String codeOf(String scanned) {
        String code = scanned == null ? "" : scanned.strip();
        if (code.regionMatches(true, 0, "CNT:", 0, 4)) {
            code = ((QrPayload.Container) QrPayload.parse(code)).code();
        }
        return code.toUpperCase(Locale.ROOT);
    }
}

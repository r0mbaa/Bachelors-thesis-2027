package io.github.r0mbaa.wms.core.topology;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Склады, зоны и места хранения (M1). Геометрию ячеек задаёт планировка, см. {@code core.layout}. */
@Service
public class TopologyService {

    /** Похоже на адрес ячейки: такой код проверяется по контрольному символу, чтобы поймать опечатку. */
    private static final Pattern CELL_ADDRESS = Pattern.compile("[A-Z][A-Z0-9]{0,5}-R\\d{2}-.*");

    private final WarehouseRepository warehouses;
    private final ZoneRepository zones;
    private final LocationRepository locations;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    TopologyService(WarehouseRepository warehouses, ZoneRepository zones, LocationRepository locations,
            AuditLog audit, ApplicationEventPublisher events, Clock clock) {
        this.warehouses = warehouses;
        this.zones = zones;
        this.locations = locations;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Создаёт склад вместе с тем, без чего он не работает: зоной по умолчанию для ячеек первой
     * планировки и виртуальными местами приёмки и отгрузки.
     *
     * <p>Зона по умолчанию — отбор с выделенным местом: в этом режиме идёт демонстрация (§11.2).
     */
    @Transactional
    public Warehouse createWarehouse(String code, String name, double defaultSpeedMps) {
        String normalized = LocationCode.requireValidWarehouse(code == null ? null : code.strip().toUpperCase(Locale.ROOT));
        if (warehouses.existsByCode(normalized)) {
            throw new ConflictException("Склад с кодом " + normalized + " уже существует: выберите другой код");
        }
        if (!(defaultSpeedMps > 0) || !Double.isFinite(defaultSpeedMps)) {
            throw new IllegalArgumentException("Скорость перемещения должна быть положительной, задано " + defaultSpeedMps);
        }
        Warehouse warehouse = warehouses.save(new Warehouse(normalized, name.strip(), defaultSpeedMps, clock.instant()));
        zones.save(new Zone(warehouse, Zone.DEFAULT_CODE, "Основная зона отбора", LocationType.PICKING,
                StoragePolicy.DEDICATED));
        locations.save(Location.virtual(warehouse, warehouse.receivingCode(), LocationType.RECEIVING));
        locations.save(Location.virtual(warehouse, warehouse.shippingCode(), LocationType.SHIPPING));
        audit.record("WAREHOUSE_CREATED", "WAREHOUSE", normalized, null,
                Map.of("name", warehouse.getName(), "defaultSpeedMps", defaultSpeedMps));
        return warehouse;
    }

    @Transactional(readOnly = true)
    public List<Warehouse> warehouses() {
        return warehouses.findAllByOrderByCode();
    }

    @Transactional(readOnly = true)
    public Warehouse warehouse(String code) {
        return warehouses.findByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Склад " + code + " не найден"));
    }

    @Transactional(readOnly = true)
    public List<Zone> zones(String warehouseCode) {
        return zones.findByWarehouseOrderByCode(warehouse(warehouseCode));
    }

    @Transactional(readOnly = true)
    public Zone zone(Warehouse warehouse, String code) {
        return zones.findByWarehouseAndCode(warehouse, code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Зона " + code + " на складе " + warehouse.getCode() + " не найдена"));
    }

    @Transactional
    public Zone createZone(String warehouseCode, String code, String name, LocationType type, StoragePolicy policy) {
        Warehouse warehouse = warehouse(warehouseCode);
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        if (zones.findByWarehouseAndCode(warehouse, normalized).isPresent()) {
            throw new ConflictException("Зона " + normalized + " уже есть на складе " + warehouse.getCode());
        }
        Zone zone = zones.save(new Zone(warehouse, normalized, name.strip(), type, policy));
        audit.record("ZONE_CREATED", "ZONE", warehouse.getCode() + "/" + normalized, null,
                Map.of("name", zone.getName(), "type", type, "storagePolicy", policy));
        return zone;
    }

    /**
     * Переводит ряды целиком в зону. Ячейки получают тип зоны; индивидуальный тип ячейки после
     * этого задаётся заново.
     *
     * @return число затронутых ячеек
     */
    @Transactional
    public int assignRows(String warehouseCode, String zoneCode, Set<Integer> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Укажите номера рядов, которые переводятся в зону");
        }
        Warehouse warehouse = warehouse(warehouseCode);
        Zone zone = zone(warehouse, zoneCode);
        int changed = locations.assignZone(warehouse, rows, zone, zone.getType());
        events.publishEvent(new RowsAssignedToZone(zone.getId(), zone.getCode(), zone.getStoragePolicy()));
        audit.record("ROWS_ASSIGNED_TO_ZONE", "ZONE", warehouse.getCode() + "/" + zone.getCode(), null,
                Map.of("rows", rows.stream().sorted().toList(), "cells", changed));
        return changed;
    }

    @Transactional(readOnly = true)
    public Location location(String code) {
        return locations.findByCode(code.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Место хранения " + code
                        + " не найдено: проверьте код на этикетке или отсканируйте её"));
    }

    /**
     * Место по тому, что отсканировали или ввели: QR {@code LOC:…} или код. Введённый вручную
     * адрес ячейки сверяется по контрольному символу, и опечатка отличается от несуществующей
     * ячейки (FR-M10-04b).
     */
    @Transactional(readOnly = true)
    public Location resolveLocation(String scanned) {
        String code = scanned == null ? "" : scanned.strip().toUpperCase(Locale.ROOT);
        if (code.startsWith("LOC:")) {
            code = ((QrPayload.Location) QrPayload.parse(code)).code().value();
        } else if (CELL_ADDRESS.matcher(code).matches()) {
            code = LocationCode.parse(code).value();
        }
        return location(code);
    }

    @Transactional(readOnly = true)
    public Page<Location> search(String warehouseCode, Integer rowNo, String zoneCode, LocationType type,
            Boolean blocked, int page, int size) {
        return locations.search(warehouse(warehouseCode), rowNo,
                zoneCode == null ? null : zoneCode.toUpperCase(Locale.ROOT), type, blocked, PageRequest.of(page, size));
    }

    /** Заблокированная ячейка недоступна для отбора и размещения (FR-M1-11). */
    @Transactional
    public Location block(String code, String reason) {
        Location location = location(code);
        String before = location.getBlockReason();
        location.block(reason);
        audit.record("LOCATION_BLOCKED", "LOCATION", location.getCode(), before, location.getBlockReason());
        return location;
    }

    @Transactional
    public Location unblock(String code) {
        Location location = location(code);
        String before = location.getBlockReason();
        location.unblock();
        audit.record("LOCATION_UNBLOCKED", "LOCATION", location.getCode(), before, null);
        return location;
    }

    /**
     * Какие классы хранения принимает ячейка (FR-M1-06): например, верхний ярус не для тяжёлого.
     * Пустой набор снимает ограничение.
     */
    @Transactional
    public Location setAllowedStorageClasses(String code, Set<StorageClass> classes) {
        Location location = location(code);
        if (!location.isCell()) {
            throw new IllegalArgumentException("Ограничение по классам хранения задаётся только ячейке стеллажа, а "
                    + location.getCode() + " — виртуальное место");
        }
        List<StorageClass> before = location.getAllowedStorageClasses().stream().sorted().toList();
        location.setAllowedStorageClasses(classes);
        audit.record("LOCATION_STORAGE_CLASSES_CHANGED", "LOCATION", location.getCode(), before,
                classes.stream().sorted().toList());
        return location;
    }

    /** Тип отдельной ячейки, отличный от типа зоны: например, одна ячейка под брак (FR-M1-05). */
    @Transactional
    public Location setType(String code, LocationType type) {
        Location location = location(code);
        if (!location.isCell() || !type.isStorage()) {
            throw new IllegalArgumentException("Тип " + type + " нельзя назначить месту " + location.getCode()
                    + ": ячейке стеллажа доступны PICKING, BULK, BUFFER, QUARANTINE, DEFECT");
        }
        LocationType before = location.getType();
        location.setType(type);
        audit.record("LOCATION_TYPE_CHANGED", "LOCATION", location.getCode(), before, type);
        return location;
    }
}

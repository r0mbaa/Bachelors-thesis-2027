package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.admin.CurrentUser;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.core.topology.WarehouseRepository;
import io.github.r0mbaa.wms.core.topology.Zone;
import io.github.r0mbaa.wms.layout.cells.Cell;
import io.github.r0mbaa.wms.layout.cells.CellGenerator;
import io.github.r0mbaa.wms.layout.model.Layout;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Хранение планировки с версиями (FR-M15-10) и перенос выведенных из неё ячеек в учёт
 * (FR-M15-05). Геометрию вычисляет библиотека {@code layout}, этот сервис её только вызывает.
 */
@Service
public class LayoutService {

    private final WarehouseRepository warehouses;
    private final LayoutVersionRepository versions;
    private final TopologyService topology;
    private final CellMaterializer materializer;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    LayoutService(WarehouseRepository warehouses, LayoutVersionRepository versions, TopologyService topology,
            CellMaterializer materializer, AuditLog audit, CurrentUser currentUser, JsonMapper json,
            ApplicationEventPublisher events, Clock clock) {
        this.warehouses = warehouses;
        this.versions = versions;
        this.topology = topology;
        this.materializer = materializer;
        this.audit = audit;
        this.currentUser = currentUser;
        this.json = json;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Сохраняет новую версию планировки.
     *
     * @param edited планировка, где {@code version} — версия, от которой шло редактирование. Если с
     *               тех пор сохранили другую, изменения не перезаписываются молча (409)
     */
    @Transactional
    public SaveResult save(String warehouseCode, Layout edited) {
        Warehouse warehouse = warehouses.lockByCode(warehouseCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Склад " + warehouseCode + " не найден"));
        if (!warehouse.getCode().equals(edited.warehouseCode())) {
            throw new IllegalArgumentException("Планировка относится к складу " + edited.warehouseCode()
                    + ", а сохраняется в склад " + warehouse.getCode() + ": исправьте код склада в документе");
        }
        long current = warehouse.getLayoutVersion();
        if (edited.version() != current) {
            throw new ConflictException("Планировку уже изменили: вы редактировали версию " + edited.version()
                    + ", текущая — " + current + ". Загрузите текущую версию и повторите изменения");
        }
        long next = current + 1;
        Layout layout = new Layout(edited.warehouseCode(), next, edited.width(), edited.length(), edited.depot(),
                edited.profiles(), edited.rows());
        List<Cell> cells = CellGenerator.generate(layout);

        Zone defaultZone = topology.zone(warehouse, Zone.DEFAULT_CODE);
        CellMaterializer.Result result = materializer.apply(warehouse.getId(), next, cells, defaultZone.getId());

        versions.save(new LayoutVersion(warehouse, next, json.writeValueAsString(layout), currentUser.username(),
                clock.instant()));
        warehouse.setLayoutVersion(next);
        events.publishEvent(new LayoutSaved(warehouse.getId(), next));
        audit.record("LAYOUT_SAVED", "LAYOUT", warehouse.getCode(), current,
                Map.of("version", next, "cells", result.cells(), "added", result.added(), "removed", result.removed()));
        return new SaveResult(next, result.cells(), result.added(), result.removed());
    }

    @Transactional(readOnly = true)
    public Layout current(String warehouseCode) {
        Warehouse warehouse = topology.warehouse(warehouseCode);
        if (warehouse.getLayoutVersion() == 0) {
            throw new NotFoundException("Планировка склада " + warehouse.getCode()
                    + " ещё не создана: откройте конструктор или мастер типовой планировки");
        }
        return version(warehouse, warehouse.getLayoutVersion());
    }

    @Transactional(readOnly = true)
    public Layout version(String warehouseCode, long version) {
        return version(topology.warehouse(warehouseCode), version);
    }

    @Transactional(readOnly = true)
    public List<LayoutVersionRepository.Summary> versions(String warehouseCode) {
        return versions.summaries(topology.warehouse(warehouseCode));
    }

    private Layout version(Warehouse warehouse, long version) {
        LayoutVersion stored = versions.findByWarehouseAndVersion(warehouse, version)
                .orElseThrow(() -> new NotFoundException("Версии " + version + " планировки склада "
                        + warehouse.getCode() + " нет"));
        return json.readValue(stored.getDocument(), Layout.class);
    }

    /**
     * @param cells   ячеек в новой версии
     * @param added   ячеек появилось
     * @param removed ячеек исчезло; строки остаются неактивными ради истории движений
     */
    public record SaveResult(long version, int cells, int added, int removed) {
    }
}

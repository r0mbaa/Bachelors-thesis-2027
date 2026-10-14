package io.github.r0mbaa.wms.core.support;

import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.catalog.CatalogService.SkuDescription;
import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.core.layout.LayoutService;
import io.github.r0mbaa.wms.core.topology.LocationType;
import io.github.r0mbaa.wms.core.topology.StoragePolicy;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.List;
import java.util.Set;
import org.springframework.boot.test.context.TestComponent;

/**
 * Типовые исходные данные тестов: склад WH1 с планировкой {@link TestLayouts#twoRows} и товары.
 * Вызывает сервисы напрямую, без проверки ролей: права проверяются в тестах API.
 */
@TestComponent
public class Fixture {

    public static final String WH = "WH1";

    private final TopologyService topology;
    private final LayoutService layouts;
    private final CatalogService catalog;

    Fixture(TopologyService topology, LayoutService layouts, CatalogService catalog) {
        this.topology = topology;
        this.layouts = layouts;
        this.catalog = catalog;
    }

    /** Склад с двумя рядами по 12 ячеек в зоне MAIN (отбор, выделенное место). */
    public void warehouse() {
        topology.createWarehouse(WH, "Тестовый склад", 1.0);
        layouts.save(WH, TestLayouts.twoRows(WH, 0));
    }

    /** Склад, где оба ряда — в зоне свободного размещения SHARED. */
    public void sharedWarehouse() {
        warehouse();
        topology.createZone(WH, "SHARED", "Свободное размещение", LocationType.PICKING, StoragePolicy.SHARED);
        topology.assignRows(WH, "SHARED", Set.of(1, 2));
    }

    /** Товар 10 × 10 × 10 см. */
    public void sku(String article, double weightKg, StorageClass storageClass) {
        catalog.create(article, new SkuDescription("Товар " + article, "шт", 0.1, 0.1, 0.1, weightKg, null,
                storageClass), List.of());
    }

    public void sku(String article) {
        sku(article, 1.0, StorageClass.NORMAL);
    }

    public static String cell(int row, int section, int level, int position) {
        return new LocationCode(WH, row, section, level, position).value();
    }
}

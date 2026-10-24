package io.github.r0mbaa.wms.core.support;

import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.catalog.CatalogService.SkuDescription;
import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.core.layout.LayoutService;
import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.order.CustomerOrder.OrderHeader;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderService.LineSpec;
import io.github.r0mbaa.wms.core.topology.LocationType;
import io.github.r0mbaa.wms.core.topology.StoragePolicy;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.time.Instant;
import java.util.ArrayList;
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
    private final OrderService orders;

    Fixture(TopologyService topology, LayoutService layouts, CatalogService catalog, OrderService orders) {
        this.topology = topology;
        this.layouts = layouts;
        this.catalog = catalog;
        this.orders = orders;
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

    /**
     * @param skuAndQuantity пары «артикул, количество»
     */
    public CustomerOrder order(String number, Instant deadline, Object... skuAndQuantity) {
        List<LineSpec> lines = new ArrayList<>();
        for (int i = 0; i < skuAndQuantity.length; i += 2) {
            lines.add(new LineSpec((String) skuAndQuantity[i], (Integer) skuAndQuantity[i + 1]));
        }
        return orders.create(WH, new OrderHeader(number, "ООО Ромашка", null, deadline, null, null, false), lines);
    }

    public CustomerOrder order(String number, Object... skuAndQuantity) {
        return order(number, null, skuAndQuantity);
    }

    public static String cell(int row, int section, int level, int position) {
        return new LocationCode(WH, row, section, level, position).value();
    }
}

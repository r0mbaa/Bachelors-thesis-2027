package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.inventory.StockLedger.Posting;
import io.github.r0mbaa.wms.core.layout.LayoutService;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.LocationType;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.layout.graph.WarehouseGraph;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Массовое заполнение склада по правилу (FR-M15-16): каждый товар — в свою пустую ячейку зоны
 * отбора, как при выделенном месте хранения (INV-08). Поступление проводится обычным движением
 * {@code RECEIPT}, поэтому журнал и сверки остаются целыми.
 *
 * <p>Корреляционное правило (FR-M14-11) требует матрицы совместной встречаемости и появится
 * вместе со слоттингом (M12).
 */
@Service
public class StockFillService {

    /**
     * Высота полки «золотой зоны», м: между поясом и плечом, прямой захват без наклона и подъёма
     * рук (§8.1). Пока функция {@code h(z)} не задана конфигурацией, правило ABC ранжирует
     * ячейки на одинаковом расстоянии по близости к этой высоте.
     */
    static final double GOLDEN_ZONE_HEIGHT = 1.0;

    private static final int MAX_CELLS = 100_000;

    private final TopologyService topology;
    private final CatalogService catalog;
    private final LayoutService layouts;
    private final StockLedger ledger;
    private final JdbcTemplate jdbc;

    StockFillService(TopologyService topology, CatalogService catalog, LayoutService layouts, StockLedger ledger,
            JdbcTemplate jdbc) {
        this.topology = topology;
        this.catalog = catalog;
        this.layouts = layouts;
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    /**
     * Строит план заполнения и, если это не пробный прогон, проводит его.
     *
     * @param dryRun только показать, куда что ляжет
     */
    @Transactional
    public FillResult fill(String warehouseCode, FillRequest request, boolean dryRun) {
        if (request.quantity() < 1) {
            throw new IllegalArgumentException("Количество на ячейку должно быть положительным");
        }
        Warehouse warehouse = topology.warehouse(warehouseCode);
        List<Sku> skus = skus(request);
        Set<Long> stocked = new HashSet<>(jdbc.queryForList("""
                select distinct s.sku_id from stock s join location l on l.id = s.location_id
                where l.warehouse_id = ? and s.quantity > 0
                """, Long.class, warehouse.getId()));
        List<Skipped> skipped = new ArrayList<>();
        List<Sku> toPlace = new ArrayList<>();
        for (Sku sku : skus) {
            if (stocked.contains(sku.getId())) {
                skipped.add(new Skipped(sku.getArticle(), "товар уже есть на складе"));
            } else {
                toPlace.add(sku);
            }
        }

        List<Location> cells = orderCells(warehouse, request);
        List<Placement> placements = new ArrayList<>();
        int next = 0;
        for (Sku sku : order(toPlace, request.rule())) {
            Placement placement = null;
            while (placement == null && next < cells.size()) {
                Location cell = cells.get(next++);
                int capacity = capacity(cell, sku);
                if (cell.accepts(sku.getStorageClass()) && capacity > 0) {
                    placement = new Placement(sku.getArticle(), cell.getCode(), Math.min(request.quantity(), capacity));
                    if (!dryRun) {
                        ledger.post(new Posting(MovementType.RECEIPT, sku, placement.quantity(), null, cell,
                                new DocumentRef("FILL", request.rule().name()), "Заполнение по правилу " + request.rule()));
                    }
                }
            }
            if (placement == null) {
                skipped.add(new Skipped(sku.getArticle(), "не нашлось свободной подходящей ячейки"));
            } else {
                placements.add(placement);
            }
        }
        return new FillResult(request.rule(), request.seed(), dryRun, placements, skipped);
    }

    private List<Sku> skus(FillRequest request) {
        if (request.skus() == null || request.skus().isEmpty()) {
            return catalog.search(null, 0, MAX_CELLS).getContent();
        }
        return request.skus().stream().map(catalog::resolve).distinct().toList();
    }

    /** Пустые доступные ячейки отбора в порядке, заданном правилом. */
    private List<Location> orderCells(Warehouse warehouse, FillRequest request) {
        List<Location> empty = new ArrayList<>(topology.search(warehouse.getCode(), null, request.zone(),
                LocationType.PICKING, false, 0, MAX_CELLS).getContent());
        Set<Long> occupied = new HashSet<>(jdbc.queryForList("""
                select distinct s.location_id from stock s join location l on l.id = s.location_id
                where l.warehouse_id = ? and s.quantity > 0
                """, Long.class, warehouse.getId()));
        empty.removeIf(cell -> occupied.contains(cell.getId()) || !cell.isCell());
        empty.sort(Comparator.comparing(Location::getCode));
        return switch (request.rule()) {
            case RANDOM -> {
                Collections.shuffle(empty, new Random(request.seed() == null ? 0 : request.seed()));
                yield empty;
            }
            case ABC -> {
                WarehouseGraph graph = layouts.graph(warehouse.getCode());
                double[] fromDepot = graph.distancesFrom(graph.depot().id());
                Map<Long, Double> distance = empty.stream().collect(Collectors.toMap(Location::getId,
                        cell -> fromDepot[graph.pickPointOf(LocationCode.parse(cell.getCode())).id()]));
                empty.sort(Comparator.comparingDouble((Location cell) -> distance.get(cell.getId()))
                        .thenComparingDouble(cell -> Math.abs(cell.getZ() - GOLDEN_ZONE_HEIGHT))
                        .thenComparing(Location::getCode));
                yield empty;
            }
        };
    }

    /** Товары класса A первыми, без класса — последними; внутри класса по артикулу. */
    private static List<Sku> order(List<Sku> skus, FillRule rule) {
        if (rule != FillRule.ABC) {
            return skus.stream().sorted(Comparator.comparing(Sku::getArticle)).toList();
        }
        return skus.stream()
                .sorted(Comparator.comparing((Sku s) -> s.getAbcClass() == null ? "Z" : s.getAbcClass())
                        .thenComparing(Sku::getArticle))
                .toList();
    }

    /** Сколько единиц товара выдержит и вместит пустая ячейка. */
    private static int capacity(Location cell, Sku sku) {
        double byWeight = cell.getMaxWeightKg() == null ? Double.MAX_VALUE : cell.getMaxWeightKg() / sku.getWeightKg();
        double byVolume = cell.getMaxVolumeM3() == null ? Double.MAX_VALUE : cell.getMaxVolumeM3() / sku.getVolumeM3();
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(Math.min(byWeight, byVolume) + 1e-9));
    }

    /**
     * @param skus     артикулы или коды товаров; пусто — весь справочник
     * @param quantity сколько положить в ячейку, если она вместит
     * @param seed     зерно случайного правила (NFR-E-01)
     * @param zone     только ячейки этой зоны; пусто — все зоны отбора
     */
    public record FillRequest(FillRule rule, List<String> skus, int quantity, Long seed, String zone) {
    }

    public record Placement(String article, String location, int quantity) {
    }

    public record Skipped(String article, String reason) {
    }

    public record FillResult(FillRule rule, Long seed, boolean dryRun, List<Placement> placements,
            List<Skipped> skipped) {
    }
}

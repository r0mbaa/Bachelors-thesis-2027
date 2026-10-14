package io.github.r0mbaa.wms.core.inventory;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.layout.LayoutService;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.support.TestLayouts;
import io.github.r0mbaa.wms.core.topology.LocationType;
import io.github.r0mbaa.wms.core.topology.StoragePolicy;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Инварианты §11.2, которые держит БД или проверяет учёт при изменении топологии. */
@IntegrationTest
class InventoryInvariantsTest {

    @Autowired
    Fixture fixture;

    @Autowired
    InventoryService inventory;

    @Autowired
    TopologyService topology;

    @Autowired
    LayoutService layouts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void movementJournalIsAppendOnly() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        inventory.place("A", cell(1, 1, 1, 1), 5, null);

        assertThatThrownBy(() -> jdbc.update("update movement set quantity = 50"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Журнал движений неизменяем");
        assertThatThrownBy(() -> jdbc.update("delete from movement"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Журнал движений неизменяем");
    }

    @Test
    void databaseRejectsNegativeAndOverReservedStock() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        inventory.place("A", cell(1, 1, 1, 1), 5, null);

        assertThatThrownBy(() -> jdbc.update("update stock set quantity = -1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("stock_quantity_non_negative");
        assertThatThrownBy(() -> jdbc.update("update stock set reserved_quantity = 6"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("stock_reserved_within_quantity");
    }

    @Test
    void integrityCheckFindsStockThatDisagreesWithJournal() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        inventory.transfer("A", cell(1, 1, 1, 1), cell(2, 1, 1, 1), 2, null);
        assertThat(inventory.checkIntegrity()).isEmpty();

        jdbc.update("update stock set quantity = 9 where quantity = 3");

        assertThat(inventory.checkIntegrity())
                .containsExactly(new InventoryService.Discrepancy(cell(1, 1, 1, 1), "A", 9, 3));
    }

    @Test
    void layoutCannotDropCellHoldingStock() {
        fixture.warehouse();
        fixture.sku("A");
        inventory.place("A", cell(1, 2, 1, 1), 1, null);

        // Ряд 1 укорочен до одной секции: ячейка с товаром исчезла бы.
        assertThatThrownBy(() -> layouts.save(Fixture.WH, TestLayouts.layout(Fixture.WH, 1,
                new RackRow(1, new Point(2, 5), Facing.NORTH, List.of("S")),
                new RackRow(2, new Point(2, 8), Facing.SOUTH, List.of("S", "S")))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(cell(1, 2, 1, 1));

        assertThat(topology.warehouse(Fixture.WH).getLayoutVersion()).isEqualTo(1);
        assertThat(topology.location(cell(1, 2, 1, 1)).isActive()).isTrue();
    }

    @Test
    void rowsCannotJoinDedicatedZoneIfSkuWouldSpanCells() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        inventory.place("A", cell(1, 1, 1, 1), 1, null);
        inventory.place("A", cell(2, 1, 1, 1), 1, null);
        topology.createZone(Fixture.WH, "DED", "Выделенное место", LocationType.PICKING, StoragePolicy.DEDICATED);

        assertThatThrownBy(() -> topology.assignRows(Fixture.WH, "DED", Set.of(1, 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("INV-08");
        assertThat(topology.location(cell(1, 1, 1, 1)).getZone().getCode()).isEqualTo("SHARED");
    }

    /**
     * Восемь потоков одновременно забирают по 3 шт. из ячейки, где лежит 10. Блокировка строк
     * остатка пропускает ровно три перемещения, остаток не уходит в минус, журнал сходится.
     */
    @Test
    void concurrentTransfersNeverOverdrawStock() throws Exception {
        fixture.sharedWarehouse();
        fixture.sku("A");
        inventory.place("A", cell(1, 1, 1, 1), 10, null);

        int threads = 8;
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String target = cell(2, 1 + i / 6, 1 + i / 2 % 3, 1 + i % 2);
            attempts.add(() -> {
                try {
                    inventory.transfer("A", cell(1, 1, 1, 1), target, 3, null);
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            });
        }
        int succeeded = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (Future<Boolean> result : pool.invokeAll(attempts)) {
                succeeded += result.get() ? 1 : 0;
            }
        }

        assertThat(succeeded).isEqualTo(3);
        assertThat(inventory.stockAt(cell(1, 1, 1, 1)).getFirst().getQuantity()).isEqualTo(1);
        assertThat(inventory.checkIntegrity()).isEmpty();
    }
}

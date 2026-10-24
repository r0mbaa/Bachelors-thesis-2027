package io.github.r0mbaa.wms.core.allocation;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.r0mbaa.wms.core.allocation.AllocationService.AllocationResult;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.inventory.Stock;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.order.WaveService;
import io.github.r0mbaa.wms.core.order.WaveService.WaveCriteria;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
@WithMockUser(roles = "DISPATCHER")
class AllocationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Fixture fixture;

    @Autowired
    InventoryService inventory;

    @Autowired
    AllocationService allocation;

    @Autowired
    OrderService orders;

    @Autowired
    WaveService waves;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        fixture.sku("B");
    }

    @Test
    void orderIsReservedFromFewestCellsAndReservedStockIsNoLongerFree() {
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        inventory.place("A", cell(1, 1, 1, 2), 3, null);
        fixture.order("SO-1", "A", 6);

        assertThat(mvc.post().uri("/api/v1/orders/SO-1/allocate"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("ALLOCATED"))
                .hasPathSatisfying("$.fullyAllocated", v -> v.assertThat().isEqualTo(true));
        assertThat(mvc.get().uri("/api/v1/orders/SO-1/allocations"))
                .bodyJson()
                .hasPathSatisfying("$[*].location", v -> v.assertThat().asArray()
                        .containsExactly(cell(1, 1, 1, 1), cell(1, 1, 1, 2)))
                .hasPathSatisfying("$[*].quantity", v -> v.assertThat().asArray().containsExactly(5, 1));

        assertThat(reserved(cell(1, 1, 1, 1))).isEqualTo(5);
        assertThatThrownBy(() -> inventory.transfer("A", cell(1, 1, 1, 1), cell(2, 1, 1, 1), 1, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("зарезервировано под заказы");
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    void shortageIsReportedAndCanBeTopUpLater() {
        inventory.place("A", cell(1, 1, 1, 1), 4, null);
        fixture.order("SO-1", "A", 6, "B", 1);

        AllocationResult first = allocation.allocateOrder("SO-1");
        assertThat(first.fullyAllocated()).isFalse();
        assertThat(first.lines()).extracting(AllocationService.LineResult::shortage).containsExactly(2, 1);

        inventory.place("A", cell(1, 1, 1, 2), 2, null);
        inventory.place("B", cell(1, 1, 2, 1), 1, null);
        assertThat(allocation.allocateOrder("SO-1").fullyAllocated()).isTrue();
    }

    @Test
    void cancellingOrderReleasesItsReservations() {
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        fixture.order("SO-1", "A", 3);
        allocation.allocateOrder("SO-1");

        orders.cancel("SO-1", "Клиент отказался");

        assertThat(reserved(cell(1, 1, 1, 1))).isZero();
        assertThat(allocation.allocationsOf("SO-1")).extracting(Allocation::getStatus)
                .containsExactly(AllocationStatus.RELEASED);
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    void manualReleaseReturnsOrderToNew() {
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        fixture.order("SO-1", "A", 3);
        allocation.allocateOrder("SO-1");

        assertThat(mvc.post().uri("/api/v1/orders/SO-1/release"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("NEW");
        assertThat(reserved(cell(1, 1, 1, 1))).isZero();
    }

    @Test
    void waveGivesScarceStockToNearestDeadlineFirst() {
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        fixture.order("LATER", Instant.parse("2026-10-22T18:00:00Z"), "A", 4);
        fixture.order("SOONER", Instant.parse("2026-10-20T18:00:00Z"), "A", 4);
        String wave = waves.form(Fixture.WH, new WaveCriteria(null, null, null, null, null)).getNumber();

        assertThat(mvc.post().uri("/api/v1/waves/{number}/allocate", wave))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("ALLOCATED"))
                .hasPathSatisfying("$.orders[*].order", v -> v.assertThat().asArray().containsExactly("SOONER", "LATER"))
                .hasPathSatisfying("$.orders[*].lines[0].allocated", v -> v.assertThat().asArray().containsExactly(4, 1));
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    void expiredReservationsAreReleasedAndOrderGoesBackToNew() {
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        fixture.order("SO-1", "A", 3);
        allocation.allocateOrder("SO-1");
        jdbc.update("update allocation set expires_at = now() - interval '1 minute'");

        allocation.releaseExpired();

        assertThat(orders.get("SO-1").getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(reserved(cell(1, 1, 1, 1))).isZero();
    }

    @Test
    void integrityCheckFindsReserveWithoutAllocation() {
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        jdbc.update("update stock set reserved_quantity = 2");

        assertThat(allocation.checkReservations())
                .containsExactly(new AllocationService.ReservationDiscrepancy(cell(1, 1, 1, 1), "A", 2, 0));
    }

    /**
     * Шесть заказов по 5 шт. одновременно резервируют ячейку, где лежит 10. Конфликт версий
     * повторяется незаметно для пользователя: ровно два заказа получают товар, остальные — ничего,
     * и резерв не превышает остатка.
     */
    @Test
    void concurrentOrderAllocationsNeverOverReserve() throws Exception {
        inventory.place("A", cell(1, 1, 1, 1), 10, null);
        int orderCount = 6;
        List<Callable<AllocationResult>> attempts = new java.util.ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            String number = "SO-" + i;
            fixture.order(number, "A", 5);
            attempts.add(() -> allocation.allocateOrder(number));
        }

        int fullyAllocated = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(orderCount)) {
            for (Future<AllocationResult> result : pool.invokeAll(attempts)) {
                fullyAllocated += result.get().fullyAllocated() ? 1 : 0;
            }
        }

        assertThat(fullyAllocated).isEqualTo(2);
        assertThat(reserved(cell(1, 1, 1, 1))).isEqualTo(10);
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerCannotAllocate() {
        fixture.order("SO-1", "A", 1);

        assertThat(mvc.post().uri("/api/v1/orders/SO-1/allocate")).hasStatus(HttpStatus.FORBIDDEN);
    }

    private int reserved(String location) {
        return inventory.stockAt(location).stream().mapToInt(Stock::getReservedQuantity).sum();
    }
}

package io.github.r0mbaa.wms.core.shipping;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static io.github.r0mbaa.wms.core.support.RunAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.r0mbaa.wms.core.admin.Role;
import io.github.r0mbaa.wms.core.admin.UserService;
import io.github.r0mbaa.wms.core.allocation.AllocationService;
import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.inventory.Stock;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.task.ContainerService;
import io.github.r0mbaa.wms.core.task.ContainerType;
import io.github.r0mbaa.wms.core.task.TaskService;
import io.github.r0mbaa.wms.core.task.TerminalService;
import io.github.r0mbaa.wms.core.task.TerminalView;
import io.github.r0mbaa.wms.core.task.WorkerService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** От сборки до отгрузки: консолидация, отгрузка с недопоставкой и инварианты INV-07, INV-09. */
@IntegrationTest
@WithMockUser(roles = "SHIPPER")
class ShippingTest {

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
    TaskService tasks;

    @Autowired
    ContainerService containers;

    @Autowired
    WorkerService workers;

    @Autowired
    UserService users;

    @Autowired
    TerminalService terminal;

    @Autowired
    ShippingService shipping;

    @BeforeEach
    void pickTwoOrdersOneOfThemShort() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        fixture.sku("B");
        inventory.place("A", cell(1, 1, 1, 1), 10, null);
        inventory.place("B", cell(2, 1, 1, 1), 5, null);
        containers.create(Fixture.WH, "C1", ContainerType.CART, 0.2, 50, 2);
        users.create("picker1", "Сборщик", "secret-pass", Set.of(Role.PICKER));
        workers.register(Fixture.WH, "picker1", "P1", "1234");
        fixture.order("SO-1", "A", 2, "B", 1);
        fixture.order("SO-2", "A", 3);
        allocation.allocateOrder("SO-1");
        allocation.allocateOrder("SO-2");
        tasks.create(Fixture.WH, List.of("SO-1", "SO-2"), "C1");

        as("picker1", Role.PICKER, () -> {
            terminal.startShift();
            terminal.claimNext();
            TerminalView view = terminal.start("CNT:C1");
            // Шаги по адресам: A для SO-1, A для SO-2 (недобор 1 шт.), B для SO-1.
            view = terminal.confirm(view.current().id(), UUID.randomUUID(), "SKU:A", null, null, false);
            view = terminal.confirm(view.current().id(), UUID.randomUUID(), "SKU:A", null, 2, false);
            terminal.confirm(view.current().id(), UUID.randomUUID(), "SKU:B", null, null, false);
            return terminal.complete();
        });
    }

    @Test
    void consolidatedOrdersShipAndShortageIsReported() {
        assertThat(mvc.post().uri("/api/v1/shipping/orders/SO-1/ship"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("сначала разложите");

        assertThat(mvc.post().uri("/api/v1/shipping/tasks/TK-000001/consolidate"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.packed", v -> v.assertThat().asArray().containsExactly("SO-1", "SO-2"))
                .hasPathSatisfying("$.moved[*].quantity", v -> v.assertThat().asArray().containsExactlyInAnyOrder(4, 1));
        assertThat(quantityAt("WH1-CNT-C1")).isZero();
        assertThat(quantityAt("WH1-SHIPPING")).isEqualTo(5);
        assertThat(shipping.checkInTransit()).isEmpty();

        assertThat(mvc.post().uri("/api/v1/shipping/orders/SO-1/ship"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("SHIPPED"))
                .hasPathSatisfying("$.shortages", v -> v.assertThat().asArray().isEmpty());
        assertThat(mvc.post().uri("/api/v1/shipping/orders/SO-2/ship"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.shortages[0].article", v -> v.assertThat().isEqualTo("A"))
                .hasPathSatisfying("$.shortages[0].shipped", v -> v.assertThat().isEqualTo(2));
        assertThat(mvc.post().uri("/api/v1/shipping/orders/SO-2/ship")).hasStatus(HttpStatus.CONFLICT);

        assertThat(quantityAt("WH1-SHIPPING")).isZero();
        assertThat(mvc.get().uri("/api/v1/skus/A/movements"))
                .bodyJson().extractingPath("$.items[*].type").asArray().contains("SHIP", "PICK", "TRANSFER");
        assertThat(orders.get("SO-2").getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipping.checkInTransit()).isEmpty();
        assertThat(inventory.checkIntegrity()).isEmpty();
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    void emptyContainerCannotBeConsolidatedTwice() {
        shipping.consolidate("TK-000001");

        assertThat(mvc.post().uri("/api/v1/shipping/tasks/TK-000001/consolidate"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("уже разложено");
    }

    @Test
    void shippingZoneAndContainersAreNotEditableByHand() {
        assertThatThrownBy(() -> inventory.transfer("A", cell(1, 1, 1, 1), "WH1-SHIPPING", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("меняется только сборкой и отгрузкой");
        assertThatThrownBy(() -> inventory.writeOff("A", "WH1-CNT-C1", 1, "Потеря"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerDoesNotShip() {
        shipping.consolidate("TK-000001");

        assertThat(mvc.post().uri("/api/v1/shipping/orders/SO-1/ship")).hasStatus(HttpStatus.FORBIDDEN);
    }

    private int quantityAt(String location) {
        return inventory.stockAt(location).stream().mapToInt(Stock::getQuantity).sum();
    }
}

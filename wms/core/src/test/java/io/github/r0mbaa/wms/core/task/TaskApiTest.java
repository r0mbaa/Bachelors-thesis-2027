package io.github.r0mbaa.wms.core.task;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.admin.Role;
import io.github.r0mbaa.wms.core.admin.UserService;
import io.github.r0mbaa.wms.core.allocation.Allocation;
import io.github.r0mbaa.wms.core.allocation.AllocationService;
import io.github.r0mbaa.wms.core.allocation.AllocationStatus;
import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@WithMockUser(username = "dispatcher", roles = "DISPATCHER")
class TaskApiTest {

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
    ContainerService containers;

    @Autowired
    WorkerService workers;

    @Autowired
    UserService users;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        fixture.sku("B");
        fixture.sku("HEAVY", 30, StorageClass.NORMAL);
        inventory.place("A", cell(1, 1, 1, 1), 10, null);
        inventory.place("B", cell(2, 2, 1, 1), 10, null);
        inventory.place("HEAVY", cell(1, 2, 1, 1), 2, null);
        containers.create(Fixture.WH, "C1", ContainerType.CART, 0.2, 50, 2);
        containers.create(Fixture.WH, "T1", ContainerType.TOTE, 0.05, 20, 1);
        fixture.order("SO-1", "A", 2, "B", 1);
        fixture.order("SO-2", "A", 3);
        allocation.allocateOrder("SO-1");
        allocation.allocateOrder("SO-2");
    }

    @Test
    void manualTaskWalksCellsInAddressOrderWithSlotPerOrder() {
        assertThat(createTask("C1", "SO-1", "SO-2"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.summary.number", v -> v.assertThat().isEqualTo("TK-000001"))
                .hasPathSatisfying("$.summary.status", v -> v.assertThat().isEqualTo("QUEUED"))
                .hasPathSatisfying("$.summary.algorithmRouting", v -> v.assertThat().isEqualTo("address_order"))
                .hasPathSatisfying("$.steps[*].sequence", v -> v.assertThat().asArray().containsExactly(1, 2, 3))
                .hasPathSatisfying("$.steps[*].location", v -> v.assertThat().asArray()
                        .containsExactly(cell(1, 1, 1, 1), cell(1, 1, 1, 1), cell(2, 2, 1, 1)))
                .hasPathSatisfying("$.steps[*].slot", v -> v.assertThat().asArray().containsExactly(1, 2, 1))
                .hasPathSatisfying("$.steps[*].order", v -> v.assertThat().asArray()
                        .containsExactly("SO-1", "SO-2", "SO-1"));

        assertThat(orders.get("SO-1").getStatus()).isEqualTo(OrderStatus.PLANNED);
        // Заказ в задании: его резервы больше не истекают.
        assertThat(allocation.allocationsOf("SO-1")).extracting(Allocation::getExpiresAt).containsOnlyNulls();
    }

    @Test
    void containerMustBeFreeRoomyAndStrongEnough() {
        createTask("C1", "SO-1");
        fixture.order("SO-3", "A", 1);
        allocation.allocateOrder("SO-3");

        assertThat(createTask("C1", "SO-3"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("уже занята");
        assertThat(createTask("T1", "SO-2", "SO-3"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("1 отделений, а заказов 2");

        fixture.order("SO-4", "HEAVY", 1);
        allocation.allocateOrder("SO-4");
        assertThat(createTask("T1", "SO-4"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("выдерживает 20.0 кг");
    }

    @Test
    void onlyAllocatedOrdersGoIntoTask() {
        fixture.order("SO-NEW", "A", 1);

        assertThat(createTask("C1", "SO-NEW"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("в статусе NEW");
    }

    @Test
    void taskIsAssignedToWorkerOnShiftAndReturnedToQueue() {
        registerPicker("picker1", "P1");
        createTask("C1", "SO-1");

        assertThat(assign("TK-000001", "WRK:P1"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("не на смене");

        jdbc.update("update worker set status = 'AVAILABLE'");
        assertThat(assign("TK-000001", "WRK:P1"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.summary.status", v -> v.assertThat().isEqualTo("ASSIGNED"))
                .hasPathSatisfying("$.summary.worker", v -> v.assertThat().isEqualTo("P1"));
        assertThat(mvc.post().uri("/api/v1/tasks/TK-000001/unassign"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.summary.status").isEqualTo("QUEUED");
    }

    @Test
    void cancellingTaskReturnsOrdersAndFreesContainer() {
        createTask("C1", "SO-1", "SO-2");

        assertThat(mvc.post().uri("/api/v1/tasks/TK-000001/cancel"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.summary.status").isEqualTo("CANCELLED");
        assertThat(orders.get("SO-1").getStatus()).isEqualTo(OrderStatus.ALLOCATED);
        assertThat(allocation.allocationsOf("SO-1")).extracting(Allocation::getExpiresAt).doesNotContainNull();

        assertThat(createTask("C1", "SO-1", "SO-2")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void cancelledOrderLeavesTaskButOthersStay() {
        createTask("C1", "SO-1", "SO-2");

        orders.cancel("SO-2", "Клиент отказался");

        assertThat(mvc.get().uri("/api/v1/tasks/TK-000001"))
                .bodyJson()
                .hasPathSatisfying("$.summary.status", v -> v.assertThat().isEqualTo("QUEUED"))
                .hasPathSatisfying("$.steps[*].status", v -> v.assertThat().asArray()
                        .containsExactly("PENDING", "CANCELLED", "PENDING"));
        assertThat(allocation.allocationsOf("SO-2")).extracting(Allocation::getStatus)
                .containsOnly(AllocationStatus.RELEASED);
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    void monitorListsActiveTasks() {
        createTask("C1", "SO-1");
        createTask("T1", "SO-2");

        assertThat(mvc.get().uri("/api/v1/tasks").param("warehouse", "WH1"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[*].number", v -> v.assertThat().asArray().hasSize(2))
                .hasPathSatisfying("$[*].stepsDone", v -> v.assertThat().asArray().containsOnly(0));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_ADMIN")
    void workerNeedsPickerRoleAndUniqueBadge() {
        users.create("storekeeper", "Кладовщик", "secret-pass", Set.of(Role.RECEIVER));
        registerPicker("picker1", "P1");
        users.create("picker2", "Сборщик 2", "secret-pass", Set.of(Role.PICKER));

        assertThat(register("storekeeper", "P9"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("нет роли PICKER");
        assertThat(register("picker2", "P1"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("уже выдан");
        assertThat(mvc.get().uri("/api/v1/workers").param("warehouse", "WH1"))
                .bodyJson().extractingPath("$[0].qrPayload").isEqualTo("WRK:P1");
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerDoesNotFormTasks() {
        assertThat(createTask("C1", "SO-1")).hasStatus(HttpStatus.FORBIDDEN);
    }

    private void registerPicker(String username, String badge) {
        users.create(username, "Сборщик", "secret-pass", Set.of(Role.PICKER));
        workers.register(Fixture.WH, username, badge, "1234");
    }

    private MvcTestResult register(String username, String badge) {
        return mvc.post().uri("/api/v1/workers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouse\": \"WH1\", \"username\": \"" + username + "\", \"code\": \"" + badge
                        + "\", \"pin\": \"1234\"}")
                .exchange();
    }

    private MvcTestResult createTask(String container, String... orders) {
        return mvc.post().uri("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouse\": \"WH1\", \"container\": \"CNT:" + container + "\", \"orders\": [\""
                        + String.join("\", \"", orders) + "\"]}")
                .exchange();
    }

    private MvcTestResult assign(String task, String worker) {
        return mvc.post().uri("/api/v1/tasks/{number}/assign", task).contentType(MediaType.APPLICATION_JSON)
                .content("{\"worker\": \"" + worker + "\"}")
                .exchange();
    }
}

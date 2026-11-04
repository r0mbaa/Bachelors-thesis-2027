package io.github.r0mbaa.wms.core.task;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static io.github.r0mbaa.wms.core.support.RunAs.as;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.admin.Role;
import io.github.r0mbaa.wms.core.admin.UserService;
import io.github.r0mbaa.wms.core.allocation.AllocationService;
import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.inventory.Stock;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.support.Json;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Сквозной сценарий терминала сборщика §6.3 с исключениями §6.4. */
@IntegrationTest
class TerminalFlowTest {

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
    TopologyService topology;

    @Autowired
    TerminalService terminal;

    private String token;

    @BeforeEach
    void prepare() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        fixture.sku("B");
        inventory.place("A", cell(1, 1, 1, 1), 10, null);
        // Второй запас A в той же зоне: по скану товара не понять, из какой ячейки он взят.
        inventory.place("A", cell(1, 2, 1, 1), 3, null);
        inventory.place("B", cell(2, 1, 1, 1), 5, null);
        containers.create(Fixture.WH, "C1", ContainerType.CART, 0.2, 50, 2);
        containers.create(Fixture.WH, "C2", ContainerType.CART, 0.2, 50, 2);
        registerPicker("picker1", "P1");
        fixture.order("SO-1", "A", 2, "B", 1);
        fixture.order("SO-2", "A", 3);
        allocation.allocateOrder("SO-1");
        allocation.allocateOrder("SO-2");
        tasks.create(Fixture.WH, List.of("SO-1", "SO-2"), "C1");
        token = login("WRK:P1", "1234");
    }

    @Test
    void pickerWalksTaskWithShortageAndDamageAndBooksStayConsistent() {
        assertThat(post("/api/v1/terminal/task/next", null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("начните смену");
        assertThat(post("/api/v1/terminal/shift/start", null)).bodyJson().extractingPath("$.status").isEqualTo("AVAILABLE");

        MvcTestResult next = post("/api/v1/terminal/task/next", null).exchange();
        assertThat(next).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.task", v -> v.assertThat().isEqualTo("TK-000001"))
                .hasPathSatisfying("$.current.location", v -> v.assertThat().isEqualTo(cell(1, 1, 1, 1)))
                .hasPathSatisfying("$.current.quantity", v -> v.assertThat().isEqualTo(2))
                .hasPathSatisfying("$.current.shelfScanRequired", v -> v.assertThat().isEqualTo(true));
        long step1 = ((Number) Json.read(next, "$.current.id")).longValue();

        assertThat(confirm(step1, UUID.randomUUID(), "SKU:A", null, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("не начато");
        assertThat(post("/api/v1/terminal/task/start", "{\"container\": \"CNT:C2\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("собирается в C1");
        assertThat(post("/api/v1/terminal/task/start", "{\"container\": \"CNT:C1\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("IN_PROGRESS");
        assertThat(orders.get("SO-1").getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        assertThat(confirm(step1, UUID.randomUUID(), "SKU:B", null, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("Отсканирован товар B");
        assertThat(confirm(step1, UUID.randomUUID(), "SKU:A", null, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("отсканируйте ещё и QR полки");

        UUID key = UUID.randomUUID();
        MvcTestResult confirmed = confirm(step1, key, "SKU:A", "LOC:" + cell(1, 1, 1, 1), null).exchange();
        assertThat(confirmed).hasStatusOk().bodyJson().extractingPath("$.stepsDone").isEqualTo(1);
        // Повторная отправка того же события (обрыв связи) не порождает второго отбора.
        assertThat(confirm(step1, key, "SKU:A", "LOC:" + cell(1, 1, 1, 1), null))
                .hasStatusOk().bodyJson().extractingPath("$.stepsDone").isEqualTo(1);
        assertThat(quantityAt(cell(1, 1, 1, 1), "A")).isEqualTo(8);
        assertThat(quantityAt("WH1-CNT-C1", "A")).isEqualTo(2);

        long step2 = ((Number) Json.read(confirmed, "$.current.id")).longValue();
        MvcTestResult shortPick = confirm(step2, UUID.randomUUID(), "SKU:A", "LOC:" + cell(1, 1, 1, 1), 2).exchange();
        assertThat(shortPick).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.current.location", v -> v.assertThat().isEqualTo(cell(2, 1, 1, 1)));

        long step3 = ((Number) Json.read(shortPick, "$.current.id")).longValue();
        assertThat(post("/api/v1/terminal/steps/" + step3 + "/exception", "{\"eventKey\": \"" + UUID.randomUUID()
                + "\", \"type\": \"DAMAGED\", \"picked\": 0, \"damaged\": 1, \"comment\": \"Разбита упаковка\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.current").isNull();
        assertThat(quantityAt(cell(2, 1, 1, 1), "B")).isEqualTo(4);

        assertThat(post("/api/v1/terminal/task/complete", null))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(orders.get("SO-1").getStatus()).isEqualTo(OrderStatus.PARTIALLY_PICKED);
        assertThat(orders.get("SO-2").getStatus()).isEqualTo(OrderStatus.PARTIALLY_PICKED);
        assertThat(quantityAt("WH1-CNT-C1", "A")).isEqualTo(4);
        assertThat(workers.get("P1").getStatus()).isEqualTo(WorkerStatus.AVAILABLE);
        assertThat(workers.get("P1").getCurrentLocation().getCode()).isEqualTo(cell(2, 1, 1, 1));
        assertThat(mvc.get().uri("/api/v1/terminal/task").header("Authorization", "Bearer " + token))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(inventory.checkIntegrity()).isEmpty();
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    void wrongPinIsRejected() {
        assertThat(mvc.post().uri("/api/v1/auth/badge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"badge\": \"WRK:P1\", \"pin\": \"9999\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Неверный бейдж или PIN");
    }

    @Test
    void pickerMayNotTypeCodesByHand() {
        long step = startTask();

        assertThat(post("/api/v1/terminal/steps/" + step + "/confirm", "{\"eventKey\": \"" + UUID.randomUUID()
                + "\", \"sku\": \"A\", \"location\": \"" + cell(1, 1, 1, 1) + "\", \"manual\": true}"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void misplacedGoodsBlockCellAndSkipStep() {
        long step = startTask();

        assertThat(post("/api/v1/terminal/steps/" + step + "/exception", "{\"eventKey\": \"" + UUID.randomUUID()
                + "\", \"type\": \"MISPLACED\", \"picked\": 0, \"damaged\": 0}"))
                .hasStatusOk();

        assertThat(topology.location(cell(1, 1, 1, 1)).getBlockReason()).contains("Пересорт", "TK-000001");
        assertThat(tasks.view("TK-000001").steps().getFirst().status()).isEqualTo(StepStatus.SKIPPED);
        assertThat(allocation.checkReservations()).isEmpty();
    }

    @Test
    void endingShiftReturnsTaskToQueueWithProgress() {
        long step = startTask();
        confirm(step, UUID.randomUUID(), "SKU:A", "LOC:" + cell(1, 1, 1, 1), null).exchange();

        assertThat(post("/api/v1/terminal/shift/end", null)).bodyJson().extractingPath("$.status").isEqualTo("OFF_SHIFT");

        TaskViews.TaskView task = tasks.view("TK-000001");
        assertThat(task.summary().status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(task.summary().worker()).isNull();
        assertThat(task.summary().stepsDone()).isEqualTo(1);
    }

    /** Два сборщика одновременно просят задание — каждый получает своё, никто не ждёт другого. */
    @Test
    void concurrentClaimsGetDistinctTasks() throws Exception {
        fixture.order("SO-3", "B", 1);
        allocation.allocateOrder("SO-3");
        tasks.create(Fixture.WH, List.of("SO-3"), "C2");
        registerPicker("picker2", "P2");
        for (String picker : List.of("picker1", "picker2")) {
            as(picker, Role.PICKER, () -> terminal.startShift());
        }

        List<Callable<Optional<TerminalView>>> claims = new ArrayList<>();
        for (String picker : List.of("picker1", "picker2")) {
            claims.add(() -> as(picker, Role.PICKER, () -> terminal.claimNext()));
        }
        List<String> claimed = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (Future<Optional<TerminalView>> result : pool.invokeAll(claims)) {
                result.get().map(TerminalView::task).ifPresent(claimed::add);
            }
        }

        assertThat(claimed).containsExactlyInAnyOrder("TK-000001", "TK-000002");
    }

    private long startTask() {
        post("/api/v1/terminal/shift/start", null).exchange();
        MvcTestResult next = post("/api/v1/terminal/task/next", null).exchange();
        post("/api/v1/terminal/task/start", "{\"container\": \"CNT:C1\"}").exchange();
        return ((Number) Json.read(next, "$.current.id")).longValue();
    }

    private MockMvcTester.MockMvcRequestBuilder confirm(long step, UUID key, String sku, String location,
            Integer quantity) {
        return post("/api/v1/terminal/steps/" + step + "/confirm", "{\"eventKey\": \"" + key + "\", \"sku\": \"" + sku
                + "\", \"location\": " + (location == null ? "null" : "\"" + location + "\"")
                + ", \"quantity\": " + quantity + "}");
    }

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri(uri).header("Authorization", "Bearer " + token);
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String login(String badge, String pin) {
        MvcTestResult result = mvc.post().uri("/api/v1/auth/badge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"badge\": \"" + badge + "\", \"pin\": \"" + pin + "\"}")
                .exchange();
        assertThat(result).hasStatusOk();
        return Json.read(result, "$.accessToken");
    }

    private void registerPicker(String username, String badge) {
        users.create(username, "Сборщик", "secret-pass", Set.of(Role.PICKER));
        workers.register(Fixture.WH, username, badge, "1234");
    }

    private int quantityAt(String location, String article) {
        return inventory.stockAt(location).stream()
                .filter(s -> s.getSku().getArticle().equals(article))
                .mapToInt(Stock::getQuantity)
                .sum();
    }
}

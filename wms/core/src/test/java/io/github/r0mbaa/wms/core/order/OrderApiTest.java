package io.github.r0mbaa.wms.core.order;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@WithMockUser(username = "dispatcher", roles = "DISPATCHER")
class OrderApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Fixture fixture;

    @Autowired
    InventoryService inventory;

    @BeforeEach
    void prepare() {
        fixture.sharedWarehouse();
        fixture.sku("A", 2.0, StorageClass.NORMAL);
        fixture.sku("B");
    }

    @Test
    void orderIsAcceptedWithLinesAndTotals() {
        assertThat(create("SO-1", 7, "2026-10-20T18:00:00+03:00", "СДЭК", false, "A", 3))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.header.status", v -> v.assertThat().isEqualTo("NEW"))
                .hasPathSatisfying("$.header.priority", v -> v.assertThat().isEqualTo(7))
                .hasPathSatisfying("$.totalWeightKg", v -> v.assertThat().isEqualTo(6.0))
                .hasPathSatisfying("$.lines[0].ordered", v -> v.assertThat().isEqualTo(3));

        assertThat(create("SO-1", 5, null, null, false, "A", 1))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("уже принят");
        assertThat(create("SO-2", 5, null, null, false, "NOPE", 1)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(create("SO-3", 10, null, null, false, "A", 1))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("от 1 до 9");
    }

    @Test
    void csvImportGroupsRowsIntoOrdersAllOrNothing() {
        String csv = """
                number;counterparty;priority;deadline;carrier;direction;sku;quantity
                SO-10;ООО Ромашка;7;2026-10-20T18:00:00+03:00;СДЭК;Север;A;2
                SO-10;ООО Ромашка;7;2026-10-20T18:00:00+03:00;СДЭК;Север;B;1
                SO-11;ИП Иванов;;;;;B;4
                """;
        assertThat(mvc.post().uri("/api/v1/orders/import").param("warehouse", "WH1")
                .contentType("text/csv").content(csv))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.orders").asArray().containsExactly("SO-10", "SO-11");
        assertThat(mvc.get().uri("/api/v1/orders/SO-10"))
                .bodyJson().extractingPath("$.lines[*].article").asArray().containsExactly("A", "B");
        assertThat(mvc.get().uri("/api/v1/orders").param("warehouse", "WH1"))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(2);
        assertThat(mvc.get().uri("/api/v1/orders").param("warehouse", "WH1").param("status", "SHIPPED"))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(0);

        String broken = """
                number,counterparty,priority,deadline,carrier,direction,sku,quantity
                SO-20,ООО Ромашка,5,,,,A,1
                SO-21,ООО Ромашка,5,,,,A,много
                """;
        assertThat(mvc.post().uri("/api/v1/orders/import").param("warehouse", "WH1")
                .contentType("text/csv").content(broken))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("Строка 3", "количество");
        assertThat(mvc.get().uri("/api/v1/orders/SO-20")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void coverageMarksLinesWithoutStock() {
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        create("SO-1", 5, null, null, false, "A", 3, "B", 2);

        assertThat(mvc.get().uri("/api/v1/orders/SO-1/coverage"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[*].status", v -> v.assertThat().asArray().containsExactly("COVERED", "SHORT"))
                .hasPathSatisfying("$[0].free", v -> v.assertThat().isEqualTo(5));
    }

    @Test
    void waveTakesNearestDeadlinesFirstAndSkipsHotAndWavedOrders() {
        create("LATE", 9, "2026-10-22T18:00:00Z", "СДЭК", false, "A", 1);
        create("EARLY", 1, "2026-10-20T18:00:00Z", "СДЭК", false, "A", 1);
        create("NO-DEADLINE", 5, null, "СДЭК", false, "A", 1);
        create("OTHER-CARRIER", 5, "2026-10-19T18:00:00Z", "Почта", false, "A", 1);
        create("HOT", 9, "2026-10-19T12:00:00Z", "СДЭК", true, "A", 1);

        assertThat(formWave("{\"warehouse\": \"WH1\", \"carrier\": \"СДЭК\", \"maxOrders\": 2}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.number", v -> v.assertThat().isEqualTo("W-000001"))
                .hasPathSatisfying("$.orders[*].number", v -> v.assertThat().asArray().containsExactly("EARLY", "LATE"))
                .hasPathSatisfying("$.criteria.carrier", v -> v.assertThat().isEqualTo("СДЭК"));

        assertThat(formWave("{\"warehouse\": \"WH1\", \"carrier\": \"СДЭК\"}"))
                .bodyJson().extractingPath("$.orders[*].number").asArray().containsExactly("NO-DEADLINE");
        assertThat(formWave("{\"warehouse\": \"WH1\", \"carrier\": \"СДЭК\"}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void cancelledOrderCannotBeCancelledAgain() {
        create("SO-1", 5, null, null, false, "A", 1);

        assertThat(mvc.post().uri("/api/v1/orders/SO-1/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Клиент передумал\"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.header.status").isEqualTo("CANCELLED");
        assertThat(mvc.post().uri("/api/v1/orders/SO-1/cancel")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerDoesNotCreateOrders() {
        assertThat(create("SO-1", 5, null, null, false, "A", 1)).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult formWave(String body) {
        return mvc.post().uri("/api/v1/waves").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult create(String number, int priority, String deadline, String carrier, boolean hot,
            Object... skuAndQuantity) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < skuAndQuantity.length; i += 2) {
            lines.append(i == 0 ? "" : ", ").append("{\"sku\": \"").append(skuAndQuantity[i])
                    .append("\", \"quantity\": ").append(skuAndQuantity[i + 1]).append('}');
        }
        String body = """
                {"warehouse": "WH1", "number": "%s", "counterparty": "ООО Ромашка", "priority": %d,
                 "deadlineAt": %s, "carrier": %s, "hot": %s, "lines": [%s]}
                """.formatted(number, priority, deadline == null ? "null" : "\"" + deadline + "\"",
                carrier == null ? "null" : "\"" + carrier + "\"", hot, lines);
        return mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}

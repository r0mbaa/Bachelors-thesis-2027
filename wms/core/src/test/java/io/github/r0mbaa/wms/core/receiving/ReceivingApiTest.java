package io.github.r0mbaa.wms.core.receiving;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
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
@WithMockUser(username = "receiver", roles = "RECEIVER")
class ReceivingApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Fixture fixture;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        fixture.sku("B");
        fixture.sku("C");
    }

    @Test
    void receiptGoesFromExpectationThroughPutawayToDiscrepancyReport() {
        assertThat(mvc.post().uri("/api/v1/receipts").contentType(MediaType.APPLICATION_JSON).content("""
                {"warehouse": "WH1", "supplier": "ООО Поставщик",
                 "lines": [{"sku": "A", "quantity": 10}, {"sku": "B", "quantity": 5}]}
                """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.number", v -> v.assertThat().isEqualTo("PR-000001"))
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("EXPECTED"));

        receive("SKU:A", 8);
        receive("B", 5);
        assertThat(receive("C", 1)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("RECEIVING");
        assertThat(mvc.get().uri("/api/v1/locations/WH1-RECEIVING/stock"))
                .bodyJson().extractingPath("$[*].quantity").asArray().containsExactly(8, 5, 1);

        // Ближайшая к депо (0, 0) пустая ячейка — первая ячейка ряда 1.
        assertThat(mvc.get().uri("/api/v1/receipts/PR-000001/recommendation").param("sku", "A").param("quantity", "8"))
                .bodyJson().extractingPath("$.location").isEqualTo(cell(1, 1, 1, 1));
        assertThat(putaway("SKU:A", "LOC:" + cell(1, 1, 1, 1), 8))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.deviation", v -> v.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.orderReversed", v -> v.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.awaitingPutaway", v -> v.assertThat().isEqualTo(0));

        // Полка отсканирована первой, и это не рекомендованная ячейка: принимается, но фиксируется.
        assertThat(putaway("LOC:" + cell(2, 1, 1, 1), "SKU:B", 5))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.orderReversed", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.recognized", v -> v.assertThat().asString().startsWith("Распознано: сначала полка"))
                .hasPathSatisfying("$.deviation", v -> v.assertThat().isEqualTo(true));
        assertThat(mvc.get().uri("/api/v1/locations/{code}/movements", cell(2, 1, 1, 1)))
                .bodyJson()
                .hasPathSatisfying("$.items[0].type", v -> v.assertThat().isEqualTo("PUTAWAY"))
                .hasPathSatisfying("$.items[0].documentId", v -> v.assertThat().isEqualTo("PR-000001"))
                .hasPathSatisfying("$.items[0].comment", v -> v.assertThat().asString()
                        .startsWith("Размещено не в рекомендованную ячейку"));

        assertThat(mvc.post().uri("/api/v1/receipts/PR-000001/close"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("CLOSED"))
                .hasPathSatisfying("$.lines[*].article", v -> v.assertThat().asArray().containsExactly("A", "B", "C"))
                .hasPathSatisfying("$.lines[*].discrepancy", v -> v.assertThat().asArray().containsExactly(-2, 0, 1));
        assertThat(receive("A", 2))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("уже закрыт");
        // Принятое, но не размещённое после закрытия всё ещё можно разместить.
        assertThat(putaway("C", cell(1, 2, 1, 1), 1)).hasStatus(HttpStatus.CREATED);
        assertThat(inventory.checkIntegrity()).isEmpty();
    }

    @Test
    void recommendationPrefersCellAlreadyHoldingSku() {
        createReceipt("A", 4);
        inventory.place("A", cell(2, 2, 3, 2), 1, null);

        assertThat(mvc.get().uri("/api/v1/receipts/PR-1/recommendation").param("sku", "A").param("quantity", "4"))
                .bodyJson().extractingPath("$.location").isEqualTo(cell(2, 2, 3, 2));
    }

    @Test
    void slowMovingSkuIsSentAwayFromDepot() {
        createReceipt("A", 1);
        jdbc.update("update sku set abc_class = 'C' where article = 'A'");

        assertThat(mvc.get().uri("/api/v1/receipts/PR-1/recommendation").param("sku", "A"))
                .bodyJson().extractingPath("$.location").isEqualTo(cell(2, 2, 1, 2));
    }

    @Test
    void putawayNeedsOneSkuOneShelfAndReceivedQuantity() {
        createReceipt("A", 4);
        receiveInto("PR-1", "A", 4);

        assertThat(putaway("SKU:A", "SKU:B", 1))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("два товара");
        assertThat(putaway("SKU:A", "LOC:" + cell(1, 1, 1, 1), 5))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("ждёт размещения 4");
        assertThat(putaway("SKU:A", "WH1-SHIPPING", 1))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("не ячейка стеллажа");
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerDoesNotReceiveGoods() {
        assertThat(mvc.get().uri("/api/v1/receipts").param("warehouse", "WH1")).hasStatus(HttpStatus.FORBIDDEN);
    }

    private void createReceipt(String sku, int quantity) {
        assertThat(mvc.post().uri("/api/v1/receipts").contentType(MediaType.APPLICATION_JSON).content(
                "{\"warehouse\": \"WH1\", \"number\": \"PR-1\", \"supplier\": \"Поставщик\", \"lines\": [{\"sku\": \""
                        + sku + "\", \"quantity\": " + quantity + "}]}"))
                .hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult receive(String sku, int quantity) {
        return receiveInto("PR-000001", sku, quantity);
    }

    private MvcTestResult receiveInto(String number, String sku, int quantity) {
        return mvc.post().uri("/api/v1/receipts/{number}/received", number).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"" + sku + "\", \"quantity\": " + quantity + "}")
                .exchange();
    }

    private MvcTestResult putaway(String first, String second, int quantity) {
        String number = mvc.get().uri("/api/v1/receipts/PR-1").exchange().getResponse().getStatus() == 200
                ? "PR-1" : "PR-000001";
        return mvc.post().uri("/api/v1/receipts/{number}/putaway", number).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scans\": [\"" + first + "\", \"" + second + "\"], \"quantity\": " + quantity + "}")
                .exchange();
    }
}

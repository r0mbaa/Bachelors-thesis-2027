package io.github.r0mbaa.wms.core.inventory;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.catalog.StorageClass;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@WithMockUser(username = "keeper", roles = "WAREHOUSE_ADMIN")
class InventoryApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Fixture fixture;

    @Autowired
    InventoryService inventory;

    @Autowired
    TopologyService topology;

    @Test
    void transferMovesStockAndBothSidesSeeItInHistory() {
        fixture.sharedWarehouse();
        fixture.sku("A");

        assertThat(place("SKU:A", "LOC:" + cell(1, 1, 1, 1), 10)).hasStatus(HttpStatus.CREATED);
        assertThat(transfer("A", cell(1, 1, 1, 1), cell(2, 1, 1, 1), 4))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.type", v -> v.assertThat().isEqualTo("TRANSFER"))
                .hasPathSatisfying("$.username", v -> v.assertThat().isEqualTo("keeper"));

        assertThat(mvc.get().uri("/api/v1/skus/A/stock"))
                .bodyJson()
                .hasPathSatisfying("$[*].location", v -> v.assertThat().asArray()
                        .containsExactly(cell(1, 1, 1, 1), cell(2, 1, 1, 1)))
                .hasPathSatisfying("$[*].quantity", v -> v.assertThat().asArray().containsExactly(6, 4));
        assertThat(mvc.get().uri("/api/v1/locations/{code}/movements", cell(1, 1, 1, 1)))
                .bodyJson().extractingPath("$.items[*].type").asArray().containsExactly("TRANSFER", "RECEIPT");
        assertThat(mvc.get().uri("/api/v1/skus/A/movements"))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(2);
    }

    @Test
    void cannotTakeMoreThanAvailable() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        place("A", cell(1, 1, 1, 1), 3);

        assertThat(transfer("A", cell(1, 1, 1, 1), cell(2, 1, 1, 1), 5))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("доступно 3 шт", "требуется 5");
        assertThat(transfer("A", cell(1, 1, 1, 2), cell(2, 1, 1, 1), 1))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("нет товара A");
    }

    @Test
    void destinationMustBeUnblockedCompatibleAndStrongEnough() {
        fixture.sharedWarehouse();
        fixture.sku("GLASS", 0.5, StorageClass.FRAGILE);
        fixture.sku("ANVIL", 30, StorageClass.HEAVY);
        topology.block(cell(1, 1, 1, 1), "Сломана полка");
        topology.setAllowedStorageClasses(cell(1, 1, 1, 2), Set.of(StorageClass.HEAVY));

        assertThat(place("GLASS", cell(1, 1, 1, 1), 1))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("заблокирована (Сломана полка)");
        assertThat(place("GLASS", cell(1, 1, 1, 2), 1))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("не принимает товар класса FRAGILE");
        // Ячейка профиля S выдерживает 75 кг: две наковальни помещаются, третья уже нет.
        assertThat(place("ANVIL", cell(1, 1, 1, 2), 2)).hasStatus(HttpStatus.CREATED);
        assertThat(place("ANVIL", cell(1, 1, 1, 2), 1))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("выдержит ещё 15.0 кг");
    }

    @Test
    void dedicatedZoneKeepsSkuInSingleCell() {
        fixture.warehouse();
        fixture.sku("A");
        place("A", cell(1, 1, 1, 1), 5);

        assertThat(place("A", cell(2, 1, 1, 1), 5))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString()
                .contains("выделенным местом", "уже лежит в ячейке " + cell(1, 1, 1, 1));
        // Перенос всего товара в другую ячейку допустим: после него товар снова в одной ячейке.
        assertThat(transfer("A", cell(1, 1, 1, 1), cell(2, 1, 1, 1), 5)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void adjustmentAndWriteOffAreRecordedAsMovementsWithReason() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        String at = cell(1, 1, 1, 1);
        place("A", at, 10);

        assertThat(adjust("A", at, 7, "Пересчёт"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.from", v -> v.assertThat().isEqualTo(at))
                .hasPathSatisfying("$.quantity", v -> v.assertThat().isEqualTo(3));
        assertThat(adjust("A", at, 12, "Найден излишек"))
                .bodyJson()
                .hasPathSatisfying("$.to", v -> v.assertThat().isEqualTo(at))
                .hasPathSatisfying("$.quantity", v -> v.assertThat().isEqualTo(5));
        assertThat(adjust("A", at, 12, "Повтор")).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.post().uri("/api/v1/inventory/write-offs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"A\", \"location\": \"" + at + "\", \"quantity\": 2, \"reason\": \"Бой\"}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.comment").isEqualTo("Бой");

        assertThat(mvc.get().uri("/api/v1/locations/{code}/stock", at))
                .bodyJson().extractingPath("$[0].quantity").isEqualTo(10);
        assertThat(mvc.get().uri("/api/v1/inventory/integrity")).bodyJson().isEqualTo("[]");
    }

    @Test
    void mistypedCellCodeIsReportedAsTypo() {
        fixture.sharedWarehouse();
        fixture.sku("A");
        String code = cell(1, 1, 1, 1);
        char wrong = code.charAt(code.length() - 1) == '0' ? '1' : '0';

        assertThat(place("A", code.substring(0, code.length() - 1) + wrong, 1))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("опечатку");
    }

    @Test
    void missingQuantityIsReportedAsFieldToFix() {
        assertThat(mvc.post().uri("/api/v1/inventory/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"A\", \"from\": \"X\", \"to\": \"Y\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("quantity");
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerCannotMoveStockByHand() {
        assertThat(transfer("A", cell(1, 1, 1, 1), cell(2, 1, 1, 1), 1)).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult place(String sku, String location, int quantity) {
        return mvc.post().uri("/api/v1/inventory/placements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"%s\", \"location\": \"%s\", \"quantity\": %d}".formatted(sku, location, quantity))
                .exchange();
    }

    private MvcTestResult transfer(String sku, String from, String to, int quantity) {
        return mvc.post().uri("/api/v1/inventory/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"quantity\": %d}"
                        .formatted(sku, from, to, quantity))
                .exchange();
    }

    private MvcTestResult adjust(String sku, String location, int actual, String reason) {
        return mvc.post().uri("/api/v1/inventory/adjustments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\": \"%s\", \"location\": \"%s\", \"actualQuantity\": %d, \"reason\": \"%s\"}"
                        .formatted(sku, location, actual, reason))
                .exchange();
    }
}

package io.github.r0mbaa.wms.core.layout;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.support.TestLayouts;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Мастер, проверка и граф планировки в API конструктора (FR-M15-06, 07, 09, 03c). */
@IntegrationTest
@WithMockUser(roles = "WAREHOUSE_ADMIN")
class LayoutDesignApiTest {

    private static final String WIZARD = """
            {"aisles": 3, "crossAisles": 2, "sectionsPerBlock": 10, "standardProfile": "Полочный 5-ярусный",
             "aisleWidth": 2.0, "crossAisleWidth": 3.0, "depotPosition": "FRONT_LEFT"}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    TopologyService topology;

    @Autowired
    Fixture fixture;

    @Autowired
    InventoryService inventory;

    @Test
    void standardProfilesAreOffered() {
        assertThat(mvc.get().uri("/api/v1/rack-profiles/standard"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].name").asArray()
                .contains("Полочный 5-ярусный", "Паллетный 3-ярусный", "Напольная зона");
    }

    @Test
    void wizardDocumentIsSavedAsIs() {
        topology.createWarehouse("WH1", "Склад", 1.0);

        MvcTestResult wizard = mvc.post().uri("/api/v1/warehouses/WH1/layout/wizard")
                .contentType(MediaType.APPLICATION_JSON).content(WIZARD).exchange();
        assertThat(wizard).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.cells", v -> v.assertThat().isEqualTo(6 * 10 * 15))
                .hasPathSatisfying("$.aislePitch", v -> v.assertThat().isEqualTo(3.2))
                .hasPathSatisfying("$.layout.version", v -> v.assertThat().isEqualTo(0));

        JsonNode layout = json.readTree(wizard.getResponse().getContentAsByteArray()).get("layout");
        assertThat(mvc.put().uri("/api/v1/warehouses/WH1/layout").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(layout)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.cells").isEqualTo(900);
    }

    @Test
    void layoutWithErrorsIsNotSavedAndIssuesPointToPlan() {
        topology.createWarehouse("WH1", "Склад", 1.0);
        Layout overlapping = TestLayouts.layout("WH1", 0,
                new RackRow(1, new Point(2, 5), Facing.NORTH, List.of("S", "S")),
                new RackRow(2, new Point(3, 5.2), Facing.SOUTH, List.of("S")));

        assertThat(save(overlapping))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .hasPathSatisfying("$.issues[*].type", v -> v.assertThat().asArray().contains("OVERLAP"))
                .hasPathSatisfying("$.issues[0].area.minX", v -> v.assertThat().isNotNull());
        assertThat(topology.warehouse("WH1").getLayoutVersion()).isZero();
    }

    @Test
    void checkShowsIssuesAndCellsThatWouldVanishWithStock() {
        fixture.warehouse();
        fixture.sku("A");
        inventory.place("A", cell(1, 2, 1, 1), 1, null);
        // Ряд 1 укорочен до одной секции: исчезнут 6 ячеек, в одной из них товар.
        Layout shorter = TestLayouts.layout(Fixture.WH, 1,
                new RackRow(1, new Point(2, 5), Facing.NORTH, List.of("S")),
                new RackRow(2, new Point(2, 8), Facing.SOUTH, List.of("S", "S")));

        assertThat(mvc.post().uri("/api/v1/warehouses/WH1/layout/check").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(shorter)))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.validation.issues", v -> v.assertThat().asArray().isEmpty())
                .hasPathSatisfying("$.changes.removed", v -> v.assertThat().isEqualTo(6))
                .hasPathSatisfying("$.changes.removedWithStock", v -> v.assertThat().asArray()
                        .containsExactly(cell(1, 2, 1, 1)));
    }

    @Test
    void graphOfCurrentVersionMapsEveryCellToPickPoint() {
        fixture.warehouse();

        assertThat(mvc.get().uri("/api/v1/warehouses/WH1/layout/graph"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.layoutVersion", v -> v.assertThat().isEqualTo(1))
                .hasPathSatisfying("$.nodes[0].kind", v -> v.assertThat().isEqualTo("DEPOT"))
                .hasPathSatisfying("$.pickPoints.length()", v -> v.assertThat().isEqualTo(24))
                .hasPathSatisfying("$.pickPoints['" + cell(1, 1, 3, 2) + "']", v -> v.assertThat().isNotNull());
    }

    private MvcTestResult save(Layout layout) {
        return mvc.put().uri("/api/v1/warehouses/{code}/layout", layout.warehouseCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(layout))
                .exchange();
    }
}

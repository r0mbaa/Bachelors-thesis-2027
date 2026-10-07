package io.github.r0mbaa.wms.core.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.support.TestLayouts;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.LocationRepository;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@WithMockUser(roles = "WAREHOUSE_ADMIN")
class LayoutApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    TopologyService topology;

    @Autowired
    LocationRepository locations;

    @BeforeEach
    void createWarehouse() {
        topology.createWarehouse("WH1", "Склад", 1.0);
    }

    @Test
    void savedLayoutMaterializesCellsWithCoordinatesAndCapacity() {
        assertThat(save(TestLayouts.twoRows("WH1", 0)))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.version", v -> v.assertThat().isEqualTo(1))
                .hasPathSatisfying("$.cells", v -> v.assertThat().isEqualTo(24))
                .hasPathSatisfying("$.added", v -> v.assertThat().isEqualTo(24));

        Location top = cell(new LocationCode("WH1", 1, 2, 3, 2));
        assertThat(top.getZone().getCode()).isEqualTo("MAIN");
        assertThat(top.getZ()).isCloseTo(0.9, within(1e-9));
        // Ряд 1 обращён на север: лицевая линия на y = 5 + 0,5.
        assertThat(top.getAccessY()).isCloseTo(5.5, within(1e-9));
        assertThat(top.getMaxWeightKg()).isCloseTo(75, within(1e-9));
        assertThat(top.qrPayload()).isEqualTo("LOC:" + top.getCode());
    }

    @Test
    void exportedDocumentIsTheImportFormat() {
        save(TestLayouts.twoRows("WH1", 0));

        MvcTestResult exported = mvc.get().uri("/api/v1/warehouses/WH1/layout").exchange();
        assertThat(exported).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(1);

        // Документ, полученный экспортом, без правок принимается как следующая версия.
        assertThat(mvc.put().uri("/api/v1/warehouses/WH1/layout").contentType(MediaType.APPLICATION_JSON)
                .content(exported.getResponse().getContentAsByteArray()))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.version", v -> v.assertThat().isEqualTo(2))
                .hasPathSatisfying("$.added", v -> v.assertThat().isEqualTo(0))
                .hasPathSatisfying("$.removed", v -> v.assertThat().isEqualTo(0));
    }

    @Test
    void movingRowKeepsCellIdentityAndRemovingSectionDeactivatesItsCells() {
        save(TestLayouts.twoRows("WH1", 0));
        long idBefore = cell(new LocationCode("WH1", 1, 1, 1, 1)).getId();

        // Ряд 1 перенесён на 3 м и укорочен до одной секции, добавлен ряд 3.
        Layout edited = TestLayouts.layout("WH1", 1,
                new RackRow(1, new Point(5, 5), Facing.NORTH, List.of("S")),
                new RackRow(2, new Point(2, 8), Facing.SOUTH, List.of("S", "S")),
                new RackRow(3, new Point(2, 11), Facing.NORTH, List.of("S")));
        assertThat(save(edited))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.cells", v -> v.assertThat().isEqualTo(24))
                .hasPathSatisfying("$.added", v -> v.assertThat().isEqualTo(6))
                .hasPathSatisfying("$.removed", v -> v.assertThat().isEqualTo(6));

        Location moved = cell(new LocationCode("WH1", 1, 1, 1, 1));
        assertThat(moved.getId()).isEqualTo(idBefore);
        assertThat(moved.getX()).isCloseTo(5.3, within(1e-9));
        assertThat(cell(new LocationCode("WH1", 1, 2, 1, 1)).isActive()).isFalse();
        assertThat(mvc.get().uri("/api/v1/warehouses/WH1/locations").param("row", "1"))
                .bodyJson().extractingPath("$.totalItems").isEqualTo(6);
    }

    @Test
    void staleBaseVersionIsRejectedInsteadOfOverwriting() {
        save(TestLayouts.twoRows("WH1", 0));

        assertThat(save(TestLayouts.twoRows("WH1", 0)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("вы редактировали версию 0, текущая — 1");
    }

    @Test
    void referentialErrorsOfDocumentComeBackAsReadableMessage() {
        String broken = json.writeValueAsString(TestLayouts.twoRows("WH1", 0))
                .replace("\"sections\":[\"S\",\"S\"]", "\"sections\":[\"S\",\"MEZZ\"]");

        assertThat(mvc.put().uri("/api/v1/warehouses/WH1/layout").contentType(MediaType.APPLICATION_JSON).content(broken))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("неизвестный профиль 'MEZZ'");
    }

    @Test
    void everyVersionStaysReadable() {
        save(TestLayouts.twoRows("WH1", 0));
        save(TestLayouts.layout("WH1", 1, new RackRow(1, new Point(2, 5), Facing.NORTH, List.of("S"))));

        assertThat(mvc.get().uri("/api/v1/warehouses/WH1/layout/versions"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].version").asArray().containsExactly(2, 1);
        assertThat(mvc.get().uri("/api/v1/warehouses/WH1/layout/versions/1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.rows.length()").isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void onlyWarehouseAdminEditsLayout() {
        assertThat(save(TestLayouts.twoRows("WH1", 0))).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult save(Layout layout) {
        return mvc.put().uri("/api/v1/warehouses/{code}/layout", layout.warehouseCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(layout))
                .exchange();
    }

    private Location cell(LocationCode code) {
        return locations.findByCode(code.value()).orElseThrow();
    }
}

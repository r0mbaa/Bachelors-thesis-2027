package io.github.r0mbaa.wms.core.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.layout.LayoutService;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.support.TestLayouts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
@WithMockUser(roles = "WAREHOUSE_ADMIN")
class TopologyApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TopologyService topology;

    @Autowired
    LayoutService layouts;

    @Test
    void newWarehouseGetsDefaultZoneAndVirtualLocations() {
        assertThat(mvc.post().uri("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"wh1\", \"name\": \"Основной склад\", \"defaultSpeedMps\": 1.0}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("WH1"))
                .hasPathSatisfying("$.layoutVersion", v -> v.assertThat().isEqualTo(0));

        assertThat(mvc.get().uri("/api/v1/warehouses/WH1/zones"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[0].code", v -> v.assertThat().isEqualTo("MAIN"))
                .hasPathSatisfying("$[0].storagePolicy", v -> v.assertThat().isEqualTo("DEDICATED"));
        assertThat(mvc.get().uri("/api/v1/locations/WH1-RECEIVING"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.type").isEqualTo("RECEIVING");
    }

    @Test
    void warehouseCodeMustFitIntoCellAddress() {
        assertThat(mvc.post().uri("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"1WH\", \"name\": \"Склад\", \"defaultSpeedMps\": 1.0}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("например WH1");
    }

    @Test
    void duplicateWarehouseIsConflict() {
        topology.createWarehouse("WH1", "Склад", 1.0);

        assertThat(mvc.post().uri("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"WH1\", \"name\": \"Другой\", \"defaultSpeedMps\": 1.0}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerReadsButCannotChangeTopology() {
        topology.createWarehouse("WH1", "Склад", 1.0);

        assertThat(mvc.get().uri("/api/v1/warehouses/WH1")).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"WH2\", \"name\": \"Склад\", \"defaultSpeedMps\": 1.0}"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void rowsMovedToZoneTakeItsType() {
        topology.createWarehouse("WH1", "Склад", 1.0);
        layouts.save("WH1", TestLayouts.twoRows("WH1", 0));

        assertThat(mvc.post().uri("/api/v1/warehouses/WH1/zones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"bulk\", \"name\": \"Навал\", \"type\": \"BULK\", \"storagePolicy\": \"SHARED\"}"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.put().uri("/api/v1/warehouses/WH1/zones/BULK/rows").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rows\": [2]}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.cells").isEqualTo(12);

        assertThat(mvc.get().uri("/api/v1/warehouses/WH1/locations").param("zone", "bulk"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.totalItems", v -> v.assertThat().isEqualTo(12))
                .hasPathSatisfying("$.items[*].type", v -> v.assertThat().asArray().containsOnly("BULK"))
                .hasPathSatisfying("$.items[*].row", v -> v.assertThat().asArray().containsOnly(2));
    }

    @Test
    void blockedCellKeepsReasonAndChangeIsAudited() {
        topology.createWarehouse("WH1", "Склад", 1.0);
        layouts.save("WH1", TestLayouts.twoRows("WH1", 0));
        String cell = topology.search("WH1", 1, null, null, null, 0, 1).getContent().getFirst().getCode();

        assertThat(mvc.post().uri("/api/v1/locations/{code}/block", cell).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Сломана полка\"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.blockReason").isEqualTo("Сломана полка");
        assertThat(mvc.get().uri("/api/v1/warehouses/WH1/locations").param("blocked", "true"))
                .bodyJson().extractingPath("$.items[*].code").asArray().containsExactly(cell);
        assertThat(mvc.get().uri("/api/v1/audit").param("entityType", "LOCATION").param("entityId", cell))
                .bodyJson().extractingPath("$.items[0].action").isEqualTo("LOCATION_BLOCKED");

        assertThat(mvc.post().uri("/api/v1/locations/{code}/unblock", cell))
                .hasStatusOk()
                .bodyJson().extractingPath("$.blocked").isEqualTo(false);
    }

    @Test
    void virtualLocationCannotGetStorageType() {
        topology.createWarehouse("WH1", "Склад", 1.0);

        assertThat(mvc.put().uri("/api/v1/locations/WH1-RECEIVING/type").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"PICKING\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownLocationTellsToCheckLabel() {
        assertThat(mvc.get().uri("/api/v1/locations/WH9-R01-01-1-1-X"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").asString().contains("проверьте код на этикетке");
    }
}

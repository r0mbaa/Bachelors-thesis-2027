package io.github.r0mbaa.wms.core.inventory;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.inventory.StockFillService.FillRequest;
import io.github.r0mbaa.wms.core.inventory.StockFillService.FillResult;
import io.github.r0mbaa.wms.core.inventory.StockFillService.Placement;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Массовое заполнение склада по правилу (FR-M15-16). */
@IntegrationTest
@WithMockUser(roles = "WAREHOUSE_ADMIN")
class StockFillTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Fixture fixture;

    @Autowired
    CatalogService catalog;

    @Autowired
    StockFillService fill;

    @Autowired
    InventoryService inventory;

    @BeforeEach
    void prepare() {
        fixture.warehouse();
        for (String article : List.of("SLOW", "HIT", "MID")) {
            fixture.sku(article);
        }
    }

    /**
     * Ближайшая к депо колонка — первая секция обоих рядов, общая точка отбора. Среди её ячеек
     * ближе всех к «золотой зоне» верхний ярус (0,9 м), поэтому класс A ложится туда первым.
     */
    @Test
    void abcRulePutsFastMoversNearDepotAtGoldenHeight() {
        assertThat(mvc.put().uri("/api/v1/skus/HIT/classes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"abc\": \"a\", \"xyz\": \"X\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.abcClass").isEqualTo("A");
        catalog.classify("MID", "B", null);
        catalog.classify("SLOW", "C", null);

        assertThat(mvc.post().uri("/api/v1/warehouses/WH1/fill").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rule\": \"ABC\", \"quantity\": 10}"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.placements[*].article", v -> v.assertThat().asArray()
                        .containsExactly("HIT", "MID", "SLOW"))
                .hasPathSatisfying("$.placements[*].location", v -> v.assertThat().asArray()
                        .containsExactly(cell(1, 1, 3, 1), cell(1, 1, 3, 2), cell(2, 1, 3, 1)));

        assertThat(inventory.stockOf("HIT")).extracting(s -> s.getLocation().getCode())
                .containsExactly(cell(1, 1, 3, 1));
        assertThat(inventory.checkIntegrity()).isEmpty();
    }

    @Test
    void randomRuleIsReproducibleBySeedAndDryRunChangesNothing() {
        FillResult first = fill.fill(Fixture.WH, new FillRequest(FillRule.RANDOM, null, 5, 42L, null), true);
        FillResult second = fill.fill(Fixture.WH, new FillRequest(FillRule.RANDOM, null, 5, 42L, null), true);
        FillResult other = fill.fill(Fixture.WH, new FillRequest(FillRule.RANDOM, null, 5, 7L, null), true);

        assertThat(first.placements()).hasSize(3).isEqualTo(second.placements());
        assertThat(first.placements()).extracting(Placement::location)
                .isNotEqualTo(other.placements().stream().map(Placement::location).toList());
        assertThat(inventory.stockOf("HIT")).isEmpty();
    }

    @Test
    void skuAlreadyOnWarehouseIsSkipped() {
        inventory.place("HIT", cell(2, 2, 1, 1), 1, null);

        FillResult result = fill.fill(Fixture.WH, new FillRequest(FillRule.RANDOM, List.of("HIT", "MID"), 5, 1L, null),
                false);

        assertThat(result.placements()).extracting(Placement::article).containsExactly("MID");
        assertThat(result.skipped()).extracting(StockFillService.Skipped::article).containsExactly("HIT");
    }

    @Test
    void quantityIsCappedByCellCapacity() {
        // Ячейка профиля S выдерживает 75 кг, товар весит 1 кг.
        FillResult result = fill.fill(Fixture.WH, new FillRequest(FillRule.ABC, List.of("HIT"), 500, null, null), false);

        assertThat(result.placements()).extracting(Placement::quantity).containsExactly(75);
    }

    @Test
    void invalidClassIsRejected() {
        assertThat(mvc.put().uri("/api/v1/skus/HIT/classes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"abc\": \"D\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}

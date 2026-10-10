package io.github.r0mbaa.wms.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.r0mbaa.wms.core.layout.LayoutService;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.support.TestLayouts;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@WithMockUser(roles = "WAREHOUSE_ADMIN")
class CatalogApiTest {

    /** Корректный EAN-13: контрольная цифра 1. */
    private static final String EAN = "4006381333931";

    private static final String MUG = """
            {"article": "85123A", "name": "Кружка белая", "uom": "шт",
             "lengthM": 0.1, "widthM": 0.1, "heightM": 0.12, "weightKg": 0.35,
             "storageClass": "FRAGILE",
             "barcodes": [{"barcode": "%s", "type": "EAN13"}]}
            """.formatted(EAN);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    CatalogService catalog;

    @Autowired
    TopologyService topology;

    @Autowired
    LayoutService layouts;

    @Test
    void createdSkuHasVolumeFromDimensionsAndQrWithArticle() {
        assertThat(create(MUG))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .hasPathSatisfying("$.volumeM3", v -> v.assertThat().asNumber().satisfies(
                        n -> assertThat(n.doubleValue()).isCloseTo(0.0012, within(1e-12))))
                .hasPathSatisfying("$.qrPayload", v -> v.assertThat().isEqualTo("SKU:85123A"))
                .hasPathSatisfying("$.barcodes[0].type", v -> v.assertThat().isEqualTo("EAN13"));
    }

    @Test
    void mistypedEanIsRejectedByCheckDigit() {
        assertThat(create(MUG.replace(EAN, "4006381333932")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("контрольная цифра");
    }

    @Test
    void articleWithSpaceCannotBeEncodedInQr() {
        assertThat(create(MUG.replace("85123A", "85123 A")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("пробелы");
    }

    @Test
    void barcodeBelongsToOneSkuOnly() {
        create(MUG);

        assertThat(create(MUG.replace("85123A", "85123B")))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("уже принадлежит товару 85123A");
    }

    @Test
    void scannedCodeResolvesByQrBarcodeOrArticle() {
        create(MUG);

        for (String code : new String[] {"SKU:85123A", "sku:85123A", EAN, "85123A"}) {
            assertThat(mvc.get().uri("/api/v1/skus/resolve").param("code", code))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.article").isEqualTo("85123A");
        }
        assertThat(mvc.get().uri("/api/v1/skus/resolve").param("code", "0000000000000"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").asString().contains("отсканируйте QR товара");
    }

    @Test
    void updateIsAuditedWithOldAndNewValues() {
        create(MUG);

        assertThat(mvc.put().uri("/api/v1/skus/85123A").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Кружка белая 300 мл", "uom": "шт", "lengthM": 0.1, "widthM": 0.1, "heightM": 0.12,
                 "weightKg": 0.4, "storageClass": "FRAGILE"}
                """))
                .hasStatusOk();

        assertThat(mvc.get().uri("/api/v1/audit").param("entityType", "SKU").param("entityId", "85123A"))
                .bodyJson()
                .hasPathSatisfying("$.items[0].action", v -> v.assertThat().isEqualTo("SKU_UPDATED"))
                .hasPathSatisfying("$.items[0].oldValue.weightKg", v -> v.assertThat().isEqualTo(0.35))
                .hasPathSatisfying("$.items[0].newValue.weightKg", v -> v.assertThat().isEqualTo(0.4));
    }

    @Test
    void searchMatchesArticleOrNameIgnoringCase() {
        create(MUG);
        create(MUG.replace("85123A", "22423").replace("Кружка белая", "Подставка для торта").replace(EAN, "4600000000008"));

        assertThat(mvc.get().uri("/api/v1/skus").param("query", "кружка"))
                .bodyJson().extractingPath("$.items[*].article").asArray().containsExactly("85123A");
        assertThat(mvc.get().uri("/api/v1/skus").param("query", "224"))
                .bodyJson().extractingPath("$.items[*].article").asArray().containsExactly("22423");
    }

    @Test
    @WithMockUser(roles = "RECEIVER")
    void receiverReadsCatalogButCannotEditIt() {
        assertThat(mvc.get().uri("/api/v1/skus")).hasStatusOk();
        assertThat(create(MUG)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void cellCanBeRestrictedToStorageClasses() {
        topology.createWarehouse("WH1", "Склад", 1.0);
        layouts.save("WH1", TestLayouts.twoRows("WH1", 0));
        String cell = topology.search("WH1", 1, null, null, null, 0, 1).getContent().getFirst().getCode();

        assertThat(mvc.put().uri("/api/v1/locations/{code}/storage-classes", cell)
                .contentType(MediaType.APPLICATION_JSON).content("{\"classes\": [\"HEAVY\", \"NORMAL\"]}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.allowedStorageClasses").asArray().containsExactly("NORMAL", "HEAVY");
        assertThat(topology.location(cell).accepts(StorageClass.FRAGILE)).isFalse();
        assertThat(topology.location(cell).accepts(StorageClass.HEAVY)).isTrue();

        assertThat(mvc.put().uri("/api/v1/locations/WH1-RECEIVING/storage-classes")
                .contentType(MediaType.APPLICATION_JSON).content("{\"classes\": [\"HEAVY\"]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult create(String body) {
        return mvc.post().uri("/api/v1/skus").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}

package io.github.r0mbaa.wms.core.print;

import static io.github.r0mbaa.wms.core.support.Fixture.cell;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import io.github.r0mbaa.wms.core.allocation.AllocationService;
import io.github.r0mbaa.wms.core.inventory.InventoryService;
import io.github.r0mbaa.wms.core.support.Fixture;
import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.task.ContainerService;
import io.github.r0mbaa.wms.core.task.ContainerType;
import io.github.r0mbaa.wms.core.task.TaskService;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Печатные формы проверяются так, как их прочтёт сканер: страница рендерится в картинку,
 * этикетка вырезается по разметке листа, QR распознаётся ZXing.
 */
@IntegrationTest
@WithMockUser(roles = {"WAREHOUSE_ADMIN", "DISPATCHER"})
class PrintApiTest {

    private static final float DPI = 200;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Fixture fixture;

    @Autowired
    TopologyService topology;

    @Autowired
    InventoryService inventory;

    @Autowired
    AllocationService allocation;

    @Autowired
    ContainerService containers;

    @Autowired
    TaskService tasks;

    @Test
    void everyCellLabelCarriesScannableQrAndReadableCode() throws Exception {
        fixture.warehouse();
        List<String> codes = topology.search(Fixture.WH, null, null, null, null, 0, 100).getContent().stream()
                .filter(Location::isCell).map(Location::getCode).toList();

        MvcTestResult result = mvc.get().uri("/api/v1/labels/locations").param("warehouse", "WH1").exchange();
        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);

        try (PDDocument pdf = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            // 24 ячейки — ровно один лист этикеток 3 × 8.
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains(codes.getFirst(), codes.getLast(), "Ряд 01 · секция 01");

            BufferedImage page = new PDFRenderer(pdf).renderImageWithDPI(0, DPI);
            List<String> decoded = new ArrayList<>();
            for (int i = 0; i < codes.size(); i++) {
                float[] origin = LabelSheet.origin(i);
                decoded.add(decode(crop(page, origin[0], origin[1], LabelSheet.WIDTH, LabelSheet.HEIGHT)));
            }
            assertThat(decoded).isEqualTo(codes.stream().map(code -> "LOC:" + code).toList());
        }
    }

    @Test
    void labelsCanBePrintedRowByRow() throws Exception {
        fixture.warehouse();

        MvcTestResult result = mvc.get().uri("/api/v1/labels/locations").param("warehouse", "WH1").param("row", "2")
                .exchange();
        try (PDDocument pdf = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains(cell(2, 1, 1, 1)).doesNotContain(cell(1, 1, 1, 1));
        }
    }

    @Test
    void skuAndContainerLabelsUseTheirPrefixes() throws Exception {
        fixture.warehouse();
        fixture.sku("85123A");
        containers.create(Fixture.WH, "C1", ContainerType.CART, 0.2, 50, 2);

        assertThat(firstQr(mvc.get().uri("/api/v1/labels/skus").param("articles", "85123A").exchange()))
                .isEqualTo("SKU:85123A");
        assertThat(firstQr(mvc.get().uri("/api/v1/labels/containers").param("warehouse", "WH1").exchange()))
                .isEqualTo("CNT:C1");
    }

    @Test
    void routeSheetListsStepsInWalkingOrderAndOpensTaskByQr() throws Exception {
        fixture.sharedWarehouse();
        fixture.sku("A");
        fixture.sku("B");
        inventory.place("A", cell(1, 1, 1, 1), 5, null);
        inventory.place("B", cell(2, 2, 3, 2), 5, null);
        containers.create(Fixture.WH, "C1", ContainerType.CART, 0.2, 50, 2);
        fixture.order("SO-1", "B", 1, "A", 2);
        allocation.allocateOrder("SO-1");
        tasks.create(Fixture.WH, List.of("SO-1"), "C1");

        MvcTestResult result = mvc.get().uri("/api/v1/tasks/TK-000001/route-sheet").exchange();
        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
        try (PDDocument pdf = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Маршрутный лист", "Задание TK-000001", "отд. 1 — SO-1", "Стр. 1 из 1");
            assertThat(text.indexOf(cell(1, 1, 1, 1))).isLessThan(text.indexOf(cell(2, 2, 3, 2)));

            BufferedImage page = new PDFRenderer(pdf).renderImageWithDPI(0, DPI);
            assertThat(decode(crop(page, PdfCanvas.PAGE_WIDTH - 50, 5, 45, 45))).isEqualTo("TSK:TK-000001");
        }
    }

    @Test
    @WithMockUser(roles = "PICKER")
    void pickerDoesNotPrintCellLabels() {
        assertThat(mvc.get().uri("/api/v1/labels/locations").param("warehouse", "WH1"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private static String firstQr(MvcTestResult result) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            BufferedImage page = new PDFRenderer(pdf).renderImageWithDPI(0, DPI);
            return decode(crop(page, 0, LabelSheet.TOP, LabelSheet.WIDTH, LabelSheet.HEIGHT));
        }
    }

    private static BufferedImage crop(BufferedImage page, float x, float y, float width, float height) {
        return page.getSubimage(px(x), px(y), px(width), px(height));
    }

    private static int px(float mm) {
        return Math.round(mm * DPI / 25.4f);
    }

    private static String decode(BufferedImage image) throws Exception {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, true)).getText();
    }
}

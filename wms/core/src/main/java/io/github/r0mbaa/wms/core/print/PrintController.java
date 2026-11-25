package io.github.r0mbaa.wms.core.print;

import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PDF для печати: этикетки листами A4 по 24 штуки и маршрутные листы. */
@RestController
@RequestMapping("/api/v1")
class PrintController {

    private final PrintService print;

    PrintController(PrintService print) {
        this.print = print;
    }

    /** QR-этикетки ячеек (FR-M1-12): всего склада, одного ряда или одной зоны. */
    @GetMapping("/labels/locations")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'RECEIVER')")
    ResponseEntity<byte[]> locations(@RequestParam String warehouse, @RequestParam(required = false) Integer row,
            @RequestParam(required = false) String zone) {
        return pdf("labels-" + warehouse + (row == null ? "" : "-R" + row), print.locationLabels(warehouse, row, zone));
    }

    @GetMapping("/labels/skus")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'RECEIVER')")
    ResponseEntity<byte[]> skus(@RequestParam(required = false) List<String> articles) {
        return pdf("labels-skus", print.skuLabels(articles));
    }

    @GetMapping("/labels/containers")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    ResponseEntity<byte[]> containers(@RequestParam String warehouse) {
        return pdf("labels-containers-" + warehouse, print.containerLabels(warehouse));
    }

    @GetMapping("/labels/workers")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'SYSTEM_ADMIN')")
    ResponseEntity<byte[]> workers(@RequestParam String warehouse) {
        return pdf("badges-" + warehouse, print.workerBadges(warehouse));
    }

    /** Маршрутный лист задания (FR-M11-03). */
    @GetMapping("/tasks/{number}/route-sheet")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN', 'PICKER')")
    ResponseEntity<byte[]> routeSheet(@PathVariable String number) {
        return pdf("route-" + number, print.routeSheet(number));
    }

    /** {@code inline}: браузер сразу открывает PDF для печати, а не скачивает его. */
    private static ResponseEntity<byte[]> pdf(String name, byte[] body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(name + ".pdf").build()
                        .toString())
                .body(body);
    }
}

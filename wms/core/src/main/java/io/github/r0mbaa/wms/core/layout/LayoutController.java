package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.core.layout.LayoutService.SaveResult;
import io.github.r0mbaa.wms.layout.model.Layout;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Документ планировки для конструктора. Его JSON одновременно служит форматом экспорта и
 * импорта (FR-M15-10): GET отдаёт документ, PUT принимает его обратно.
 */
@RestController
@RequestMapping("/api/v1/warehouses/{code}/layout")
class LayoutController {

    private final LayoutService layouts;

    LayoutController(LayoutService layouts) {
        this.layouts = layouts;
    }

    @GetMapping
    Layout current(@PathVariable String code) {
        return layouts.current(code);
    }

    @PutMapping
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    SaveResult save(@PathVariable String code, @RequestBody Layout layout) {
        return layouts.save(code, layout);
    }

    @GetMapping("/versions")
    List<LayoutVersionRepository.Summary> versions(@PathVariable String code) {
        return layouts.versions(code);
    }

    @GetMapping("/versions/{version}")
    Layout version(@PathVariable String code, @PathVariable long version) {
        return layouts.version(code, version);
    }
}

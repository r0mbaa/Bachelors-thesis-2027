package io.github.r0mbaa.wms.core.catalog;

import io.github.r0mbaa.wms.core.catalog.CatalogService.BarcodeSpec;
import io.github.r0mbaa.wms.core.catalog.CatalogService.SkuDescription;
import io.github.r0mbaa.wms.core.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skus")
class SkuController {

    private final CatalogService catalog;

    SkuController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    PageResponse<SkuView> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(catalog.search(query, page, PageResponse.clampSize(size)), SkuView::from);
    }

    @GetMapping("/{article}")
    SkuView get(@PathVariable String article) {
        return SkuView.from(catalog.get(article));
    }

    /** Что за товар отсканирован: QR системы, штрихкод производителя или артикул. */
    @GetMapping("/resolve")
    SkuView resolve(@RequestParam String code) {
        return SkuView.from(catalog.resolve(code));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    SkuView create(@Valid @RequestBody CreateSkuRequest request) {
        return SkuView.from(catalog.create(request.article(), request.description(),
                request.barcodes() == null ? List.of() : request.barcodes()));
    }

    @PutMapping("/{article}")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    SkuView update(@PathVariable String article, @Valid @RequestBody SkuFields request) {
        return SkuView.from(catalog.update(article, request.description()));
    }

    @PostMapping("/{article}/barcodes")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    SkuView addBarcode(@PathVariable String article, @Valid @RequestBody BarcodeRequest request) {
        return SkuView.from(catalog.addBarcode(article, new BarcodeSpec(request.barcode(), request.type())));
    }

    @DeleteMapping("/{article}/barcodes/{barcode}")
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    SkuView removeBarcode(@PathVariable String article, @PathVariable String barcode) {
        return SkuView.from(catalog.removeBarcode(article, barcode));
    }

    record CreateSkuRequest(
            @NotBlank String article,
            @NotBlank String name,
            @NotBlank String uom,
            @Positive double lengthM,
            @Positive double widthM,
            @Positive double heightM,
            @Positive double weightKg,
            @Positive Double volumeM3,
            StorageClass storageClass,
            List<@Valid BarcodeSpec> barcodes) {

        SkuDescription description() {
            return new SkuDescription(name, uom, lengthM, widthM, heightM, weightKg, volumeM3, storageClass);
        }
    }

    record SkuFields(
            @NotBlank String name,
            @NotBlank String uom,
            @Positive double lengthM,
            @Positive double widthM,
            @Positive double heightM,
            @Positive double weightKg,
            @Positive Double volumeM3,
            StorageClass storageClass) {

        SkuDescription description() {
            return new SkuDescription(name, uom, lengthM, widthM, heightM, weightKg, volumeM3, storageClass);
        }
    }

    record BarcodeRequest(@NotBlank String barcode, @NotNull BarcodeType type) {
    }
}

package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.common.PageResponse;
import io.github.r0mbaa.wms.core.inventory.InventoryService.Discrepancy;
import io.github.r0mbaa.wms.core.inventory.InventoryViews.MovementView;
import io.github.r0mbaa.wms.core.inventory.InventoryViews.StockView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class InventoryController {

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/locations/{code}/stock")
    List<StockView> stockAt(@PathVariable String code) {
        return inventory.stockAt(code).stream().map(StockView::from).toList();
    }

    @GetMapping("/skus/{article}/stock")
    List<StockView> stockOf(@PathVariable String article) {
        return inventory.stockOf(article).stream().map(StockView::from).toList();
    }

    @GetMapping("/warehouses/{code}/stock")
    PageResponse<StockView> stockIn(@PathVariable String code,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return PageResponse.of(inventory.stockIn(code, page, PageResponse.clampSize(size)), StockView::from);
    }

    @GetMapping("/skus/{article}/movements")
    PageResponse<MovementView> historyOf(@PathVariable String article,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(inventory.historyOf(article, page, PageResponse.clampSize(size)), MovementView::from);
    }

    @GetMapping("/locations/{code}/movements")
    PageResponse<MovementView> historyAt(@PathVariable String code,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(inventory.historyAt(code, page, PageResponse.clampSize(size)), MovementView::from);
    }

    @PostMapping("/inventory/placements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'RECEIVER')")
    MovementView place(@Valid @RequestBody PlacementRequest request) {
        return MovementView.from(inventory.place(request.sku(), request.location(), request.quantity(),
                request.comment()));
    }

    @PostMapping("/inventory/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'RECEIVER', 'DISPATCHER')")
    MovementView transfer(@Valid @RequestBody TransferRequest request) {
        return MovementView.from(inventory.transfer(request.sku(), request.from(), request.to(), request.quantity(),
                request.comment()));
    }

    @PostMapping("/inventory/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    MovementView adjust(@Valid @RequestBody AdjustmentRequest request) {
        return MovementView.from(inventory.adjust(request.sku(), request.location(), request.actualQuantity(),
                request.reason()));
    }

    @PostMapping("/inventory/write-offs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAREHOUSE_ADMIN')")
    MovementView writeOff(@Valid @RequestBody WriteOffRequest request) {
        return MovementView.from(inventory.writeOff(request.sku(), request.location(), request.quantity(),
                request.reason()));
    }

    /** INV-04: пустой список — остатки совпадают с журналом. */
    @GetMapping("/inventory/integrity")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ANALYST')")
    List<Discrepancy> integrity() {
        return inventory.checkIntegrity();
    }

    /**
     * @param sku      QR товара, штрихкод или артикул
     * @param location QR ячейки или её код
     */
    record PlacementRequest(@NotBlank String sku, @NotBlank String location, @Positive int quantity, String comment) {
    }

    record TransferRequest(@NotBlank String sku, @NotBlank String from, @NotBlank String to, @Positive int quantity,
            String comment) {
    }

    record AdjustmentRequest(@NotBlank String sku, @NotBlank String location, @PositiveOrZero int actualQuantity,
            @NotBlank String reason) {
    }

    record WriteOffRequest(@NotBlank String sku, @NotBlank String location, @Positive int quantity,
            @NotBlank String reason) {
    }
}

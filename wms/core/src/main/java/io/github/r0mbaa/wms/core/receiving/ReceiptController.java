package io.github.r0mbaa.wms.core.receiving;

import io.github.r0mbaa.wms.core.receiving.ReceivingService.ExpectedLine;
import io.github.r0mbaa.wms.core.receiving.ReceivingService.PutawayResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/v1/receipts")
@PreAuthorize("hasAnyRole('RECEIVER', 'WAREHOUSE_ADMIN')")
class ReceiptController {

    private final ReceivingService receiving;

    ReceiptController(ReceivingService receiving) {
        this.receiving = receiving;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReceiptView create(@Valid @RequestBody CreateReceiptRequest request) {
        return ReceiptView.from(receiving.create(request.warehouse(), request.number(), request.supplier(),
                request.lines().stream().map(l -> new ExpectedLine(l.sku(), l.quantity())).toList()));
    }

    @GetMapping
    List<ReceiptView> list(@RequestParam String warehouse, @RequestParam(defaultValue = "true") boolean open) {
        return receiving.list(warehouse, open).stream().map(ReceiptView::from).toList();
    }

    @GetMapping("/{number}")
    ReceiptView get(@PathVariable String number) {
        return ReceiptView.from(receiving.get(number));
    }

    /** Приёмка по факту: товар поступает в зону приёмки (§6.2, шаги 2–3). */
    @PostMapping("/{number}/received")
    ReceiptView receive(@PathVariable String number, @Valid @RequestBody ReceiveRequest request) {
        return ReceiptView.from(receiving.receive(number, request.sku(), request.quantity()));
    }

    /** Рекомендованная ячейка (FR-M3-03); {@code location = null} — подходящей ячейки нет. */
    @GetMapping("/{number}/recommendation")
    Map<String, String> recommend(@PathVariable String number, @RequestParam String sku,
            @RequestParam(defaultValue = "1") @Positive int quantity) {
        Map<String, String> body = new HashMap<>();
        body.put("location", receiving.recommend(number, sku, quantity).orElse(null));
        return body;
    }

    @PostMapping("/{number}/putaway")
    @ResponseStatus(HttpStatus.CREATED)
    PutawayResult putaway(@PathVariable String number, @Valid @RequestBody PutawayRequest request) {
        return receiving.putaway(number, request.scans(), request.quantity());
    }

    @PostMapping("/{number}/close")
    ReceiptView close(@PathVariable String number) {
        return ReceiptView.from(receiving.close(number));
    }

    /**
     * @param number номер документа поставщика; без него присваивается внутренний
     */
    record CreateReceiptRequest(@NotBlank String warehouse, String number, @NotBlank String supplier,
            @NotEmpty List<@Valid LineRequest> lines) {
    }

    record LineRequest(@NotBlank String sku, @Positive int quantity) {
    }

    record ReceiveRequest(@NotBlank String sku, @Positive int quantity) {
    }

    /**
     * @param scans ровно два сканирования: товар и полка, в любом порядке
     */
    record PutawayRequest(@Size(min = 2, max = 2) List<@NotBlank String> scans, @Positive int quantity) {
    }
}

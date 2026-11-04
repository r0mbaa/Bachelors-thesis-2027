package io.github.r0mbaa.wms.core.shipping;

import io.github.r0mbaa.wms.core.shipping.ShippingService.Consolidation;
import io.github.r0mbaa.wms.core.shipping.ShippingService.InTransitDiscrepancy;
import io.github.r0mbaa.wms.core.shipping.ShippingService.Shipment;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping")
class ShippingController {

    private final ShippingService shipping;

    ShippingController(ShippingService shipping) {
        this.shipping = shipping;
    }

    /** Раскладка тары собранного задания в зону отгрузки (FR-M11-01). */
    @PostMapping("/tasks/{number}/consolidate")
    @PreAuthorize("hasAnyRole('SHIPPER', 'DISPATCHER')")
    Consolidation consolidate(@PathVariable String number) {
        return shipping.consolidate(number);
    }

    @PostMapping("/orders/{number}/ship")
    @PreAuthorize("hasRole('SHIPPER')")
    Shipment ship(@PathVariable String number) {
        return shipping.ship(number);
    }

    /** INV-09: пустой список — собранное в таре и зоне отгрузки сходится с заказами. */
    @GetMapping("/integrity")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ANALYST')")
    List<InTransitDiscrepancy> integrity() {
        return shipping.checkInTransit();
    }
}

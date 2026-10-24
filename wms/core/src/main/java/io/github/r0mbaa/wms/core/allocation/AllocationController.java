package io.github.r0mbaa.wms.core.allocation;

import io.github.r0mbaa.wms.core.allocation.AllocationService.AllocationResult;
import io.github.r0mbaa.wms.core.allocation.AllocationService.ReservationDiscrepancy;
import io.github.r0mbaa.wms.core.allocation.AllocationService.WaveAllocationResult;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AllocationController {

    private final AllocationService allocation;

    AllocationController(AllocationService allocation) {
        this.allocation = allocation;
    }

    @PostMapping("/orders/{number}/allocate")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
    AllocationResult allocateOrder(@PathVariable String number) {
        return allocation.allocateOrder(number);
    }

    @PostMapping("/orders/{number}/release")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
    AllocationResult release(@PathVariable String number) {
        return allocation.releaseOrder(number);
    }

    @GetMapping("/orders/{number}/allocations")
    List<AllocationView> allocations(@PathVariable String number) {
        return allocation.allocationsOf(number).stream().map(AllocationView::from).toList();
    }

    @PostMapping("/waves/{number}/allocate")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
    WaveAllocationResult allocateWave(@PathVariable String number) {
        return allocation.allocateWave(number);
    }

    /** INV-03: пустой список — резервы в остатках совпадают с активными аллокациями. */
    @GetMapping("/allocations/integrity")
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'ANALYST')")
    List<ReservationDiscrepancy> integrity() {
        return allocation.checkReservations();
    }

    record AllocationView(int lineNo, String article, String location, int quantity, int picked,
            AllocationStatus status, Instant reservedAt, Instant expiresAt) {

        static AllocationView from(Allocation a) {
            return new AllocationView(a.getOrderLine().getLineNo(), a.getSku().getArticle(), a.getLocation().getCode(),
                    a.getQuantity(), a.getQuantityPicked(), a.getStatus(), a.getReservedAt(), a.getExpiresAt());
        }
    }
}

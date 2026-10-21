package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.common.PageResponse;
import io.github.r0mbaa.wms.core.order.CustomerOrder.OrderHeader;
import io.github.r0mbaa.wms.core.order.OrderService.LineCoverage;
import io.github.r0mbaa.wms.core.order.OrderService.LineSpec;
import io.github.r0mbaa.wms.core.order.OrderViews.OrderSummary;
import io.github.r0mbaa.wms.core.order.OrderViews.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
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
@RequestMapping("/api/v1/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
    OrderView create(@Valid @RequestBody CreateOrderRequest request) {
        return OrderView.from(orders.create(request.warehouse(), request.header(),
                request.lines().stream().map(l -> new LineSpec(l.sku(), l.quantity())).toList()));
    }

    /** Импорт выгрузки заказов (FR-M5-02); формат описан в {@link OrderCsvParser}. */
    @PostMapping(value = "/import", consumes = "text/csv")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
    Map<String, Object> importCsv(@RequestParam String warehouse, @RequestBody String csv) {
        List<String> numbers = orders.importCsv(warehouse, csv).stream().map(CustomerOrder::getNumber).toList();
        return Map.of("created", numbers.size(), "orders", numbers);
    }

    @GetMapping
    PageResponse<OrderSummary> search(
            @RequestParam String warehouse,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(orders.search(warehouse, status, page, PageResponse.clampSize(size)),
                OrderSummary::from);
    }

    @GetMapping("/{number}")
    OrderView get(@PathVariable String number) {
        return OrderView.from(orders.get(number));
    }

    /** Обеспеченность строк остатками (FR-M5-04). */
    @GetMapping("/{number}/coverage")
    List<LineCoverage> coverage(@PathVariable String number) {
        return orders.coverage(number);
    }

    @PostMapping("/{number}/cancel")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
    OrderView cancel(@PathVariable String number, @RequestBody(required = false) CancelRequest request) {
        return OrderView.from(orders.cancel(number, request == null ? null : request.reason()));
    }

    /**
     * @param priority от 1 до 9, по умолчанию 5
     * @param hot      срочный заказ обрабатывается вне волны (FR-M5-08)
     */
    record CreateOrderRequest(
            @NotBlank String warehouse,
            @NotBlank String number,
            @NotBlank String counterparty,
            Integer priority,
            Instant deadlineAt,
            String carrier,
            String direction,
            boolean hot,
            @NotEmpty List<@Valid LineRequest> lines) {

        OrderHeader header() {
            return new OrderHeader(number, counterparty, priority, deadlineAt, carrier, direction, hot);
        }
    }

    record LineRequest(@NotBlank String sku, @Positive int quantity) {
    }

    record CancelRequest(String reason) {
    }
}

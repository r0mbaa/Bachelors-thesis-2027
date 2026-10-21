package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.order.OrderViews.OrderSummary;
import io.github.r0mbaa.wms.core.order.WaveService.WaveCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/waves")
class WaveController {

    private final WaveService waves;
    private final JsonMapper json;

    WaveController(WaveService waves, JsonMapper json) {
        this.waves = waves;
        this.json = json;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
    WaveView form(@Valid @RequestBody FormWaveRequest request) {
        return view(waves.form(request.warehouse(), request.criteria()));
    }

    @GetMapping
    List<WaveView> list(@RequestParam String warehouse) {
        return waves.list(warehouse).stream().map(this::view).toList();
    }

    @GetMapping("/{number}")
    WaveView get(@PathVariable String number) {
        return view(waves.get(number));
    }

    private WaveView view(Wave wave) {
        return new WaveView(wave.getNumber(), wave.getWarehouse().getCode(), wave.getStatus(),
                json.readTree(wave.getCriteria()), wave.getCreatedBy(), wave.getCreatedAt(),
                waves.ordersOf(wave).stream().map(OrderSummary::from).toList());
    }

    record FormWaveRequest(@NotBlank String warehouse, Instant deadlineBefore, String carrier, String direction,
            Integer minPriority, Integer maxOrders) {

        WaveCriteria criteria() {
            return new WaveCriteria(deadlineBefore, carrier, direction, minPriority, maxOrders);
        }
    }

    record WaveView(String number, String warehouse, WaveStatus status, JsonNode criteria, String createdBy,
            Instant createdAt, List<OrderSummary> orders) {
    }
}

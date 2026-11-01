package io.github.r0mbaa.wms.core.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API терминала сборщика (M10). Каждый ответ — текущее состояние задания, которое терминал
 * показывает целиком; отдельных запросов «что дальше» не нужно.
 */
@RestController
@RequestMapping("/api/v1/terminal")
@PreAuthorize("hasRole('PICKER')")
class TerminalController {

    private final TerminalService terminal;

    TerminalController(TerminalService terminal) {
        this.terminal = terminal;
    }

    @PostMapping("/shift/start")
    Map<String, WorkerStatus> startShift() {
        return Map.of("status", terminal.startShift());
    }

    @PostMapping("/shift/break")
    Map<String, WorkerStatus> takeBreak() {
        return Map.of("status", terminal.takeBreak());
    }

    @PostMapping("/shift/end")
    Map<String, WorkerStatus> endShift() {
        return Map.of("status", terminal.endShift());
    }

    /** Текущее задание; 204 — заданий нет. */
    @GetMapping("/task")
    ResponseEntity<TerminalView> current() {
        return terminal.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Следующее задание из очереди; 204 — очередь пуста. */
    @PostMapping("/task/next")
    ResponseEntity<TerminalView> next() {
        return terminal.claimNext().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/task/start")
    TerminalView start(@Valid @RequestBody StartRequest request) {
        return terminal.start(request.container());
    }

    @PostMapping("/steps/{stepId}/confirm")
    TerminalView confirm(@PathVariable long stepId, @Valid @RequestBody ConfirmRequest request) {
        return terminal.confirm(stepId, request.eventKey(), request.sku(), request.location(), request.quantity(),
                request.manual());
    }

    @PostMapping("/steps/{stepId}/exception")
    TerminalView reportException(@PathVariable long stepId, @Valid @RequestBody ExceptionRequest request) {
        return terminal.reportException(stepId, request.eventKey(), request.type(), request.picked(), request.damaged(),
                request.comment());
    }

    @PostMapping("/task/complete")
    TerminalView complete() {
        return terminal.complete();
    }

    /** @param container скан QR тележки {@code CNT:…} */
    record StartRequest(@NotBlank String container) {
    }

    /**
     * @param eventKey ключ идемпотентности, генерируется терминалом на каждое действие (FR-M10-08)
     * @param sku      скан QR товара
     * @param location скан QR полки, если терминал его запросил
     * @param quantity {@code null} — требуемое по заданию (FR-M10-04c)
     * @param manual   код введён вручную (FR-M10-04b)
     */
    record ConfirmRequest(@NotNull UUID eventKey, @NotBlank String sku, String location, Integer quantity,
            boolean manual) {
    }

    record ExceptionRequest(@NotNull UUID eventKey, @NotNull PickException type, @PositiveOrZero int picked,
            @PositiveOrZero int damaged, String comment) {
    }
}

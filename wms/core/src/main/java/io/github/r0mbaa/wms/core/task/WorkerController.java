package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.shared.marking.QrPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/workers")
class WorkerController {

    private final WorkerService workers;

    WorkerController(WorkerService workers) {
        this.workers = workers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'SYSTEM_ADMIN')")
    WorkerView register(@Valid @RequestBody RegisterRequest request) {
        return WorkerView.from(workers.register(request.warehouse(), request.username(), request.code(),
                request.pin()));
    }

    /** Реестр сборщиков со статусами и текущими позициями (FR-M9-01). */
    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN', 'SYSTEM_ADMIN')")
    List<WorkerView> list(@RequestParam String warehouse) {
        return workers.list(warehouse).stream().map(WorkerView::from).toList();
    }

    @PutMapping("/{code}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('WAREHOUSE_ADMIN', 'SYSTEM_ADMIN')")
    void setPin(@PathVariable String code, @Valid @RequestBody PinRequest request) {
        workers.setPin(code, request.pin());
    }

    /** @param code код бейджа, он же QR {@code WRK:…} */
    record RegisterRequest(@NotBlank String warehouse, @NotBlank String username, @NotBlank String code,
            @NotBlank String pin) {
    }

    record PinRequest(@NotBlank String pin) {
    }

    /** @param currentLocation последняя подтверждённая ячейка */
    record WorkerView(String code, String username, String fullName, WorkerStatus status, String currentLocation,
            String qrPayload) {

        static WorkerView from(Worker w) {
            return new WorkerView(w.getCode(), w.getUser().getUsername(), w.getUser().getFullName(), w.getStatus(),
                    w.getCurrentLocation() == null ? null : w.getCurrentLocation().getCode(),
                    new QrPayload.Worker(w.getCode()).encode());
        }
    }
}

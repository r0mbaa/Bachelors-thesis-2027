package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.task.TaskViews.TaskSummary;
import io.github.r0mbaa.wms.core.task.TaskViews.TaskView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

/** Задания глазами диспетчера: формирование, монитор, назначение (M9). */
@RestController
@RequestMapping("/api/v1/tasks")
@PreAuthorize("hasAnyRole('DISPATCHER', 'WAREHOUSE_ADMIN')")
class TaskController {

    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    /** Ручное формирование задания (NFR-R-05): работает и без планировщика. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskView create(@Valid @RequestBody CreateTaskRequest request) {
        return tasks.view(tasks.create(request.warehouse(), request.orders(), request.container()).getNumber());
    }

    /** Монитор диспетчера (FR-M9-07): без статуса — все активные задания. */
    @GetMapping
    List<TaskSummary> list(@RequestParam String warehouse, @RequestParam(required = false) TaskStatus status) {
        return tasks.list(warehouse, status);
    }

    @GetMapping("/{number}")
    TaskView get(@PathVariable String number) {
        return tasks.view(number);
    }

    @PostMapping("/{number}/assign")
    TaskView assign(@PathVariable String number, @Valid @RequestBody AssignRequest request) {
        return tasks.view(tasks.assign(number, request.worker()).getNumber());
    }

    @PostMapping("/{number}/unassign")
    TaskView unassign(@PathVariable String number) {
        return tasks.view(tasks.unassign(number).getNumber());
    }

    @PutMapping("/{number}/priority")
    TaskView setPriority(@PathVariable String number, @Valid @RequestBody PriorityRequest request) {
        return tasks.view(tasks.setPriority(number, request.priority()).getNumber());
    }

    @PostMapping("/{number}/cancel")
    TaskView cancel(@PathVariable String number) {
        return tasks.view(tasks.cancel(number).getNumber());
    }

    /**
     * @param orders    заказы в порядке отделений тележки
     * @param container код тары или скан её QR
     */
    record CreateTaskRequest(@NotBlank String warehouse, @NotEmpty List<@NotBlank String> orders,
            @NotBlank String container) {
    }

    /** @param worker код бейджа сборщика */
    record AssignRequest(@NotBlank String worker) {
    }

    record PriorityRequest(int priority) {
    }
}

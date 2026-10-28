package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.allocation.Allocation;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Задание на сборку: батч, назначенный сборщику, с маршрутом из шагов (§4, FR-M9-01..06). */
@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.QUEUED;

    private int priority;

    private Instant deadlineAt;

    private Instant createdAt;

    private Instant assignedAt;

    private Instant startedAt;

    private Instant finishedAt;

    @Column(name = "actual_duration_s")
    private Double actualDurationS;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    @OrderBy("sequence")
    private List<TaskStep> steps = new ArrayList<>();

    @Version
    private long version;

    protected Task() {
    }

    Task(String number, Warehouse warehouse, Batch batch, int priority, Instant deadlineAt, Instant createdAt) {
        this.number = number;
        this.warehouse = warehouse;
        this.batch = batch;
        this.priority = priority;
        this.deadlineAt = deadlineAt;
        this.createdAt = createdAt;
    }

    TaskStep newStep(Allocation allocation, int slot) {
        TaskStep step = new TaskStep(this, steps.size() + 1, allocation, slot);
        steps.add(step);
        return step;
    }

    void assign(Worker worker, Instant now) {
        requireActive();
        this.worker = worker;
        this.assignedAt = now;
        if (status == TaskStatus.QUEUED) {
            status = TaskStatus.ASSIGNED;
        }
    }

    /** Возврат в очередь с сохранением пройденных шагов (FR-M9-06). */
    void unassign() {
        requireActive();
        this.worker = null;
        this.status = TaskStatus.QUEUED;
    }

    void start(Instant now) {
        if (status != TaskStatus.ASSIGNED && status != TaskStatus.IN_PROGRESS) {
            throw new ConflictException("Задание " + number + " в статусе " + status + ": начать можно назначенное задание");
        }
        if (startedAt == null) {
            startedAt = now;
        }
        status = TaskStatus.IN_PROGRESS;
    }

    void complete(Instant now) {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new ConflictException("Задание " + number + " в статусе " + status + ": завершить можно только начатое");
        }
        if (nextStep().isPresent()) {
            throw new ConflictException("В задании " + number + " остались непройденные шаги: пройдите их или "
                    + "зарегистрируйте исключение");
        }
        status = TaskStatus.COMPLETED;
        finishedAt = now;
        actualDurationS = Duration.between(startedAt, now).toMillis() / 1000.0;
    }

    void cancel(Instant now) {
        if (status != TaskStatus.QUEUED && status != TaskStatus.ASSIGNED) {
            throw new ConflictException("Задание " + number + " в статусе " + status
                    + ": отменить можно только не начатое задание");
        }
        status = TaskStatus.CANCELLED;
        finishedAt = now;
        worker = null;
    }

    void setPriority(int priority) {
        requireActive();
        this.priority = priority;
    }

    void requireActive() {
        if (!status.isActive()) {
            throw new ConflictException("Задание " + number + " уже в статусе " + status);
        }
    }

    /** Первый непройденный шаг: маршрут идёт строго по порядку. */
    public Optional<TaskStep> nextStep() {
        return steps.stream().filter(s -> !s.getStatus().isDone()).findFirst();
    }

    public long doneSteps() {
        return steps.stream().filter(s -> s.getStatus().isDone()).count();
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Batch getBatch() {
        return batch;
    }

    public Worker getWorker() {
        return worker;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Double getActualDurationS() {
        return actualDurationS;
    }

    public List<TaskStep> getSteps() {
        return List.copyOf(steps);
    }
}

package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.allocation.Allocation;
import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.order.OrderLine;
import io.github.r0mbaa.wms.core.topology.Location;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Шаг маршрута: из какой ячейки, какой товар, сколько и в какое отделение тележки. */
@Entity
@Table(name = "task_step")
public class TaskStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    private int sequence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_id")
    private OrderLine orderLine;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id")
    private Allocation allocation;

    private int quantityRequired;

    private int quantityPicked;

    private int containerSlot;

    @Enumerated(EnumType.STRING)
    private StepStatus status = StepStatus.PENDING;

    private Instant confirmedAt;

    @Enumerated(EnumType.STRING)
    private PickException exceptionType;

    private String comment;

    protected TaskStep() {
    }

    TaskStep(Task task, int sequence, Allocation allocation, int containerSlot) {
        this.task = task;
        this.sequence = sequence;
        this.location = allocation.getLocation();
        this.sku = allocation.getSku();
        this.orderLine = allocation.getOrderLine();
        this.allocation = allocation;
        this.quantityRequired = allocation.getQuantity();
        this.containerSlot = containerSlot;
    }

    /**
     * Закрывает шаг.
     *
     * @param picked    отобрано фактически
     * @param exception исключение, если шаг прошёл не по плану
     */
    void complete(StepStatus result, int picked, PickException exception, String comment, Instant now) {
        requirePending();
        this.status = result;
        this.quantityPicked = picked;
        this.exceptionType = exception;
        this.comment = comment;
        this.confirmedAt = now;
    }

    void cancel(Instant now) {
        requirePending();
        this.status = StepStatus.CANCELLED;
        this.confirmedAt = now;
    }

    void requirePending() {
        if (status.isDone()) {
            throw new ConflictException("Шаг " + sequence + " задания " + task.getNumber() + " уже закрыт ("
                    + status + "): перейдите к следующему шагу");
        }
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public int getSequence() {
        return sequence;
    }

    public Location getLocation() {
        return location;
    }

    public Sku getSku() {
        return sku;
    }

    public OrderLine getOrderLine() {
        return orderLine;
    }

    public Allocation getAllocation() {
        return allocation;
    }

    public int getQuantityRequired() {
        return quantityRequired;
    }

    public int getQuantityPicked() {
        return quantityPicked;
    }

    public int getContainerSlot() {
        return containerSlot;
    }

    public StepStatus getStatus() {
        return status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public PickException getExceptionType() {
        return exceptionType;
    }

    public String getComment() {
        return comment;
    }
}

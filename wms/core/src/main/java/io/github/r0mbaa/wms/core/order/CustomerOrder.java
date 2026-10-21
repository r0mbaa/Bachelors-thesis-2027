package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Заказ на отгрузку (FR-M5-01). */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 9;
    public static final int DEFAULT_PRIORITY = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private String number;

    private String counterparty;

    private int priority;

    private Instant deadlineAt;

    private String carrier;

    private String direction;

    private boolean hot;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wave_id")
    private Wave wave;

    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<OrderLine> lines = new ArrayList<>();

    @Version
    private long version;

    protected CustomerOrder() {
    }

    CustomerOrder(Warehouse warehouse, OrderHeader header, Instant createdAt) {
        int priority = header.priority() == null ? DEFAULT_PRIORITY : header.priority();
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("Приоритет заказа " + header.number() + " должен быть от "
                    + MIN_PRIORITY + " до " + MAX_PRIORITY + ", задано " + priority);
        }
        this.warehouse = warehouse;
        this.number = header.number();
        this.counterparty = header.counterparty();
        this.priority = priority;
        this.deadlineAt = header.deadlineAt();
        this.carrier = blankToNull(header.carrier());
        this.direction = blankToNull(header.direction());
        this.hot = header.hot();
        this.createdAt = createdAt;
    }

    void addLine(Sku sku, int quantity) {
        lines.add(new OrderLine(this, lines.size() + 1, sku, quantity));
    }

    void joinWave(Wave wave) {
        this.wave = wave;
    }

    /**
     * Перевод статуса с проверкой, что заказ сейчас в одном из допустимых исходных состояний.
     * Модули аллокации, заданий и отгрузки двигают заказ только так.
     */
    public void transition(OrderStatus target, Set<OrderStatus> allowedFrom) {
        if (!allowedFrom.contains(status)) {
            throw new ConflictException("Заказ " + number + " в статусе " + status + ", а переход в " + target
                    + " возможен только из " + allowedFrom);
        }
        status = target;
    }

    void cancel() {
        if (!status.isCancellable()) {
            throw new ConflictException("Заказ " + number + " уже в статусе " + status
                    + ": отменить можно только до начала сборки, дальше — возврат собранного на хранение");
        }
        status = OrderStatus.CANCELLED;
    }

    /** Все строки полностью зарезервированы. */
    public boolean isFullyAllocated() {
        return lines.stream().allMatch(l -> l.unallocated() == 0);
    }

    public double totalWeightKg() {
        return lines.stream().mapToDouble(l -> l.getQuantityOrdered() * l.getSku().getWeightKg()).sum();
    }

    public double totalVolumeM3() {
        return lines.stream().mapToDouble(l -> l.getQuantityOrdered() * l.getSku().getVolumeM3()).sum();
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getNumber() {
        return number;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isHot() {
        return hot;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Wave getWave() {
        return wave;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderLine> getLines() {
        return List.copyOf(lines);
    }

    public static Set<OrderStatus> from(OrderStatus first, OrderStatus... rest) {
        return EnumSet.of(first, rest);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Реквизиты заказа без строк.
     *
     * @param priority {@code null} — средний приоритет
     */
    public record OrderHeader(String number, String counterparty, Integer priority, Instant deadlineAt,
            String carrier, String direction, boolean hot) {
    }
}

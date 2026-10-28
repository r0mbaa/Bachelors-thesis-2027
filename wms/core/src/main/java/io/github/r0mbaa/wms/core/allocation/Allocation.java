package io.github.r0mbaa.wms.core.allocation;

import io.github.r0mbaa.wms.core.catalog.Sku;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Резерв: сколько товара в какой ячейке закреплено за строкой заказа (FR-M6-01). */
@Entity
@Table(name = "allocation")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_id")
    private OrderLine orderLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    private int quantity;

    private int quantityPicked;

    @Enumerated(EnumType.STRING)
    private AllocationStatus status = AllocationStatus.RESERVED;

    private Instant reservedAt;

    private Instant expiresAt;

    private Instant closedAt;

    @Version
    private long version;

    protected Allocation() {
    }

    Allocation(OrderLine orderLine, Location location, Sku sku, int quantity, Instant reservedAt, Instant expiresAt) {
        this.orderLine = orderLine;
        this.location = location;
        this.sku = sku;
        this.quantity = quantity;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
    }

    /** @param expiresAt {@code null} — бессрочно, пока заказ в задании */
    void expireAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    void release(Instant now) {
        close(AllocationStatus.RELEASED, now);
    }

    /**
     * Закрывает резерв по итогам отбора.
     *
     * @param picked отобрано фактически; меньше резерва — недостача в ячейке
     */
    void pick(int picked, Instant now) {
        quantityPicked = picked;
        close(picked > 0 ? AllocationStatus.PICKED : AllocationStatus.FAILED, now);
    }

    private void close(AllocationStatus target, Instant now) {
        if (status != AllocationStatus.RESERVED) {
            throw new IllegalStateException("Резерв " + id + " уже закрыт со статусом " + status);
        }
        status = target;
        closedAt = now;
    }

    public Long getId() {
        return id;
    }

    public OrderLine getOrderLine() {
        return orderLine;
    }

    public Location getLocation() {
        return location;
    }

    public Sku getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getQuantityPicked() {
        return quantityPicked;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

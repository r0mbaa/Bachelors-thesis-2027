package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.order.Wave;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Батч: заказы, собираемые за один проход по складу (§8.3), с отделением тележки под каждый
 * заказ. Хранит, каким алгоритмом батчинга и маршрутизации он построен, чтобы результаты
 * планирования были воспроизводимы и сравнимы.
 */
@Entity
@Table(name = "batch")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wave_id")
    private Wave wave;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "container_id")
    private Container container;

    private String algorithmBatching;

    private String algorithmRouting;

    @Column(name = "total_volume_m3")
    private double totalVolumeM3;

    @Column(name = "total_weight_kg")
    private double totalWeightKg;

    @Column(name = "estimated_distance_m")
    private Double estimatedDistanceM;

    @Column(name = "estimated_duration_s")
    private Double estimatedDurationS;

    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "batch_order", joinColumns = @JoinColumn(name = "batch_id"))
    @OrderBy("slot")
    private List<BatchOrder> orders = new ArrayList<>();

    protected Batch() {
    }

    Batch(Warehouse warehouse, Wave wave, Container container, String algorithmBatching, String algorithmRouting,
            double totalVolumeM3, double totalWeightKg, Instant createdAt) {
        this.warehouse = warehouse;
        this.wave = wave;
        this.container = container;
        this.algorithmBatching = algorithmBatching;
        this.algorithmRouting = algorithmRouting;
        this.totalVolumeM3 = totalVolumeM3;
        this.totalWeightKg = totalWeightKg;
        this.createdAt = createdAt;
    }

    void addOrder(CustomerOrder order, int slot) {
        orders.add(new BatchOrder(order, slot));
    }

    void clearOrders() {
        orders.clear();
    }

    public Container getContainer() {
        return container;
    }

    public Wave getWave() {
        return wave;
    }

    public String getAlgorithmBatching() {
        return algorithmBatching;
    }

    public String getAlgorithmRouting() {
        return algorithmRouting;
    }

    public double getTotalVolumeM3() {
        return totalVolumeM3;
    }

    public double getTotalWeightKg() {
        return totalWeightKg;
    }

    public Double getEstimatedDistanceM() {
        return estimatedDistanceM;
    }

    public Double getEstimatedDurationS() {
        return estimatedDurationS;
    }

    public List<BatchOrder> getOrders() {
        return List.copyOf(orders);
    }

    /** Заказ и его отделение тележки. */
    @Embeddable
    public static class BatchOrder {

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id")
        private CustomerOrder order;

        private int slot;

        protected BatchOrder() {
        }

        BatchOrder(CustomerOrder order, int slot) {
            this.order = order;
            this.slot = slot;
        }

        public CustomerOrder getOrder() {
            return order;
        }

        public int getSlot() {
            return slot;
        }
    }
}

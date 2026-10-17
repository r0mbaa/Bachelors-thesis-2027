package io.github.r0mbaa.wms.core.receiving;

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
import java.util.List;
import java.util.Optional;

/** Документ прихода (FR-M3-01): ожидаемые позиции поставки и факт их приёмки и размещения. */
@Entity
@Table(name = "receipt")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    private String number;

    private String supplier;

    @Enumerated(EnumType.STRING)
    private ReceiptStatus status = ReceiptStatus.EXPECTED;

    private String createdBy;

    private Instant createdAt;

    private Instant closedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReceiptLine> lines = new ArrayList<>();

    @Version
    private long version;

    protected Receipt() {
    }

    Receipt(Warehouse warehouse, String number, String supplier, String createdBy, Instant createdAt) {
        this.warehouse = warehouse;
        this.number = number;
        this.supplier = supplier;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    void expect(Sku sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Ожидаемое количество товара " + sku.getArticle() + " должно быть положительным");
        }
        if (line(sku).isPresent()) {
            throw new IllegalArgumentException("Товар " + sku.getArticle() + " указан в документе дважды: объедините строки");
        }
        lines.add(new ReceiptLine(this, sku, quantity));
    }

    /**
     * Фиксирует принятое по факту (FR-M3-02). Товар не из документа принимается строкой с
     * нулевым ожиданием, чтобы излишек попал в акт расхождений, а не потерялся.
     */
    ReceiptLine receive(Sku sku, int quantity) {
        requireOpen();
        ReceiptLine line = line(sku).orElseGet(() -> {
            ReceiptLine surplus = new ReceiptLine(this, sku, 0);
            lines.add(surplus);
            return surplus;
        });
        line.receive(quantity);
        status = ReceiptStatus.RECEIVING;
        return line;
    }

    void close(Instant now) {
        requireOpen();
        status = ReceiptStatus.CLOSED;
        closedAt = now;
    }

    void requireOpen() {
        if (status == ReceiptStatus.CLOSED) {
            throw new ConflictException("Приход " + number + " уже закрыт: оформите новый документ прихода");
        }
    }

    Optional<ReceiptLine> line(Sku sku) {
        return lines.stream().filter(l -> l.getSku().getId().equals(sku.getId())).findFirst();
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

    public String getSupplier() {
        return supplier;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public List<ReceiptLine> getLines() {
        return List.copyOf(lines);
    }
}

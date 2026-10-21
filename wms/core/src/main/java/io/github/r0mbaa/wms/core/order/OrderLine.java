package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.catalog.Sku;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Строка заказа. Количества меняют аллокация, сборка и отгрузка; их пределы проверяет и БД. */
@Entity
@Table(name = "order_line")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    private int quantityOrdered;

    private int quantityAllocated;

    private int quantityPicked;

    private int quantityShipped;

    protected OrderLine() {
    }

    OrderLine(CustomerOrder order, int lineNo, Sku sku, int quantityOrdered) {
        if (quantityOrdered <= 0) {
            throw new IllegalArgumentException("Строка " + lineNo + ": заказанное количество товара " + sku.getArticle()
                    + " должно быть положительным");
        }
        this.order = order;
        this.lineNo = lineNo;
        this.sku = sku;
        this.quantityOrdered = quantityOrdered;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Sku getSku() {
        return sku;
    }

    public int getQuantityOrdered() {
        return quantityOrdered;
    }

    public int getQuantityAllocated() {
        return quantityAllocated;
    }

    public int getQuantityPicked() {
        return quantityPicked;
    }

    public int getQuantityShipped() {
        return quantityShipped;
    }

    /** Ещё не обеспечено резервом. */
    public int unallocated() {
        return quantityOrdered - quantityAllocated;
    }

    public void allocate(int quantity) {
        quantityAllocated += quantity;
    }

    public void deallocate(int quantity) {
        quantityAllocated -= quantity;
    }

    public void pick(int quantity) {
        quantityPicked += quantity;
    }

    public void ship(int quantity) {
        quantityShipped += quantity;
    }
}

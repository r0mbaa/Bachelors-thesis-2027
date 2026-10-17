package io.github.r0mbaa.wms.core.receiving;

import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.common.ConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "receipt_line")
public class ReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    private int quantityExpected;

    private int quantityReceived;

    private int quantityPutAway;

    protected ReceiptLine() {
    }

    ReceiptLine(Receipt receipt, Sku sku, int quantityExpected) {
        this.receipt = receipt;
        this.sku = sku;
        this.quantityExpected = quantityExpected;
    }

    void receive(int quantity) {
        quantityReceived += quantity;
    }

    void putAway(int quantity) {
        if (quantity > awaitingPutaway()) {
            throw new ConflictException("По приходу " + receipt.getNumber() + " товара " + sku.getArticle()
                    + " ждёт размещения " + awaitingPutaway() + " " + sku.getUom() + ", а размещается " + quantity
                    + ": проверьте количество или сначала примите товар");
        }
        quantityPutAway += quantity;
    }

    public Sku getSku() {
        return sku;
    }

    public int getQuantityExpected() {
        return quantityExpected;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public int getQuantityPutAway() {
        return quantityPutAway;
    }

    /** Принято, но ещё лежит в зоне приёмки. */
    public int awaitingPutaway() {
        return quantityReceived - quantityPutAway;
    }

    /** Расхождение с документом: больше нуля — излишек, меньше — недостача. */
    public int discrepancy() {
        return quantityReceived - quantityExpected;
    }
}

package io.github.r0mbaa.wms.core.catalog;

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

@Entity
@Table(name = "sku_barcode")
public class SkuBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    private String barcode;

    @Enumerated(EnumType.STRING)
    private BarcodeType type;

    protected SkuBarcode() {
    }

    SkuBarcode(Sku sku, String barcode, BarcodeType type) {
        if (type == null) {
            throw new IllegalArgumentException("Укажите тип штрихкода " + barcode + ": EAN13, CODE128 или INTERNAL");
        }
        this.sku = sku;
        this.barcode = type.requireValid(barcode);
        this.type = type;
    }

    public Sku getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public BarcodeType getType() {
        return type;
    }
}

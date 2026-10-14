package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.topology.Location;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Остаток SKU в месте хранения (FR-M4-01). Меняется только через {@link StockLedger}, вместе с
 * записью движения. Строка создаётся при первом приходе и остаётся с нулём после расхода.
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    private int quantity;

    private int reservedQuantity;

    private Instant updatedAt;

    /** Оптимистическая блокировка для конкурентной аллокации (FR-M6-05, NFR-R-02). */
    @Version
    private long version;

    protected Stock() {
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

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    /** Доступно для нового резерва или перемещения (FR-M4-02). */
    public int available() {
        return quantity - reservedQuantity;
    }

    void add(int amount, Instant now) {
        quantity += amount;
        updatedAt = now;
    }

    /** Расход из свободной части остатка: зарезервированное под заказы трогать нельзя. */
    void remove(int amount, Instant now) {
        if (amount > available()) {
            throw new ConflictException("В месте " + location.getCode() + " доступно " + available() + " "
                    + sku.getUom() + " товара " + sku.getArticle() + (reservedQuantity > 0
                            ? " (ещё " + reservedQuantity + " зарезервировано под заказы)" : "")
                    + ", а требуется " + amount + ": уменьшите количество или выберите другое место");
        }
        quantity -= amount;
        updatedAt = now;
    }
}

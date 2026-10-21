package io.github.r0mbaa.wms.core.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Статусная модель заказа (FR-M5-03):
 * {@code NEW → ALLOCATED → PLANNED → IN_PROGRESS → PICKED | PARTIALLY_PICKED → PACKED → SHIPPED},
 * а также {@code CANCELLED}.
 */
public enum OrderStatus {
    NEW,
    /** Под строки зарезервированы ячейки. */
    ALLOCATED,
    /** Заказ вошёл в задание на сборку. */
    PLANNED,
    IN_PROGRESS,
    PICKED,
    /** Сборка завершена, но часть строк не собрана: нет товара, повреждён. */
    PARTIALLY_PICKED,
    /** Собранное разложено по заказам в зоне отгрузки. */
    PACKED,
    SHIPPED,
    CANCELLED;

    /** До начала отбора заказ отменяется освобождением резервов (FR-M5-07). */
    private static final Set<OrderStatus> CANCELLABLE = EnumSet.of(NEW, ALLOCATED, PLANNED);

    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }
}

package io.github.r0mbaa.wms.core.order;

public enum WaveStatus {
    /** Заказы отобраны в волну, резервов ещё нет. */
    FORMED,
    /** Под заказы волны зарезервированы ячейки. */
    ALLOCATED,
    COMPLETED,
    CANCELLED
}

package io.github.r0mbaa.wms.core.receiving;

public enum ReceiptStatus {
    /** Поставка ожидается, ничего ещё не принято. */
    EXPECTED,
    /** Идёт приёмка и размещение. */
    RECEIVING,
    /** Приёмка закрыта, расхождения зафиксированы. Принятое, но не размещённое остаётся в зоне приёмки. */
    CLOSED
}

package io.github.r0mbaa.wms.core.task;

public enum ContainerType {
    /** Одиночный короб: одно отделение. */
    TOTE,
    /** Тележка с несколькими отделениями под заказы батча (§6.3, sort-while-pick). */
    CART
}

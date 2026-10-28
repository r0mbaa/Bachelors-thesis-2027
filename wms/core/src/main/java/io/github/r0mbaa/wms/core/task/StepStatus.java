package io.github.r0mbaa.wms.core.task;

public enum StepStatus {
    /** Ещё не пройден. */
    PENDING,
    /** Отобрано всё требуемое. */
    PICKED,
    /** Отобрано меньше требуемого или ничего: нет товара, недостаточно, повреждён. */
    SHORT,
    /** Точка пропущена: ячейка недоступна или обнаружен пересорт. */
    SKIPPED,
    /** Заказ отменён до начала задания. */
    CANCELLED;

    public boolean isDone() {
        return this != PENDING;
    }
}

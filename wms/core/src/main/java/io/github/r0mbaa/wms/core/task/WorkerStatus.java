package io.github.r0mbaa.wms.core.task;

/** Статус сборщика (FR-M9-01). */
public enum WorkerStatus {
    OFF_SHIFT,
    /** На смене и свободен: может получить задание. */
    AVAILABLE,
    /** Выполняет задание. */
    BUSY,
    BREAK
}

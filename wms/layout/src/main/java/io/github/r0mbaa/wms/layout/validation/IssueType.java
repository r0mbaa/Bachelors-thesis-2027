package io.github.r0mbaa.wms.layout.validation;

/** Вид замечания к планировке (FR-M15-07). */
public enum IssueType {
    /** На плане нет стеллажей. */
    EMPTY,
    /** Ряд выходит за границы склада. */
    OUT_OF_BOUNDS,
    /** Ряды пересекаются. */
    OVERLAP,
    /** Перед лицевой стороной меньше {@code w_min}: тележка не пройдёт (§8.7, Б2). */
    AISLE_TOO_NARROW,
    /** Депо за границами склада, внутри стеллажа или вплотную к нему. */
    DEPOT_BLOCKED,
    /** До точек отбора ряда нельзя дойти от депо. */
    UNREACHABLE
}

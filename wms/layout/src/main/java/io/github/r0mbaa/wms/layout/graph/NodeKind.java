package io.github.r0mbaa.wms.layout.graph;

/** Вид узла графа склада (§8.1: {@code V = V_p ∪ V_c ∪ {d}}). */
public enum NodeKind {
    /** Точка старта и финиша маршрутов. */
    DEPOT,
    /** Точка отбора: проекция колонки стеллажа на ось прохода. */
    PICK_POINT,
    /** Пересечение или поворот осей проходов. */
    INTERSECTION
}

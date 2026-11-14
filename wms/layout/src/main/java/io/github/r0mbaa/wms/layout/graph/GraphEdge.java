package io.github.r0mbaa.wms.layout.graph;

/**
 * Неориентированное ребро. Все рёбра идут вдоль осей, поэтому длина равна разности координат
 * концов, а для ребра, заменившего цепочку прямых отрезков, — их сумме.
 *
 * @param from   меньший из номеров концов
 * @param length длина, м
 */
public record GraphEdge(int from, int to, double length) {
}

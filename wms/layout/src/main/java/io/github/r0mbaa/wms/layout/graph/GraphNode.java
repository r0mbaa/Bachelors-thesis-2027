package io.github.r0mbaa.wms.layout.graph;

import io.github.r0mbaa.wms.layout.model.Point;

/**
 * @param id       номер узла в графе: депо — 0, затем точки отбора, затем пересечения; каждая группа
 *                 по возрастанию координат. Номера детерминированы для одной версии планировки
 * @param position точка на плане, м
 */
public record GraphNode(int id, NodeKind kind, Point position) {
}

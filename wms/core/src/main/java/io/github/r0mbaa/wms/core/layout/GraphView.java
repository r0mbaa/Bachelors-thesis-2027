package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.layout.graph.GraphEdge;
import io.github.r0mbaa.wms.layout.graph.GraphNode;
import io.github.r0mbaa.wms.layout.graph.WarehouseGraph;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Граф склада в API.
 *
 * @param pickPoints код ячейки → номер её точки отбора
 * @param markStep   шаг отметок вдоль прохода {@code d_mark}, м
 */
public record GraphView(long layoutVersion, double markStep, List<GraphNode> nodes, List<GraphEdge> edges,
        Map<String, Integer> pickPoints) {

    static GraphView from(WarehouseGraph graph) {
        Map<String, Integer> pickPoints = new TreeMap<>();
        graph.pickPoints().forEach((code, node) -> pickPoints.put(code.value(), node));
        return new GraphView(graph.layoutVersion(), graph.markStep(), graph.nodes(), graph.edges(), pickPoints);
    }
}

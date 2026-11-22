package io.github.r0mbaa.wms.layout.graph;

import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Граф склада {@code G = (V, E, w)} (§8.1), выведенный из планировки. Рёбра лежат на осях
 * проходов и поперечных проходов, координаты узлов точные, поэтому длины путей не несут
 * погрешности растеризации (§8.7, Д1).
 *
 * @param layoutVersion версия планировки, из которой построен граф: ключ кэша матрицы
 *                      расстояний (§8.7, Д4)
 * @param pickPoints    ячейка → номер её точки отбора. Все ячейки одной колонки стеллажа
 *                      отображаются в одну точку (§1.5)
 * @param markStep      шаг отметок вдоль прохода {@code d_mark}, м (§8.7, А2)
 */
public record WarehouseGraph(
        long layoutVersion,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        Map<LocationCode, Integer> pickPoints,
        double markStep,
        GraphSettings settings) {

    public WarehouseGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        pickPoints = Map.copyOf(pickPoints);
    }

    public GraphNode depot() {
        return nodes.getFirst();
    }

    public GraphNode node(int id) {
        return nodes.get(id);
    }

    public GraphNode pickPointOf(LocationCode cell) {
        Integer id = pickPoints.get(cell);
        if (id == null) {
            throw new IllegalArgumentException("Ячейки " + cell + " нет в планировке версии " + layoutVersion);
        }
        return nodes.get(id);
    }

    /** Список смежности: для каждого узла — рёбра, которые из него выходят. */
    public List<List<GraphEdge>> adjacency() {
        List<List<GraphEdge>> adjacency = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            adjacency.add(new ArrayList<>());
        }
        for (GraphEdge edge : edges) {
            adjacency.get(edge.from()).add(edge);
            adjacency.get(edge.to()).add(new GraphEdge(edge.to(), edge.from(), edge.length()));
        }
        return adjacency;
    }

    /**
     * Кратчайшие расстояния по графу от узла до всех остальных (Дейкстра на двоичной куче).
     * Недостижимые узлы получают {@code +∞}. Полная матрица расстояний с кэшем строится в
     * {@code planner-engine}; здесь — одиночный источник, например депо для правил размещения.
     */
    public double[] distancesFrom(int source) {
        List<List<GraphEdge>> adjacency = adjacency();
        double[] dist = new double[nodes.size()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        PriorityQueue<double[]> queue = new PriorityQueue<>(Comparator.comparingDouble(entry -> entry[1]));
        queue.add(new double[] {source, 0});
        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int node = (int) top[0];
            if (top[1] > dist[node]) {
                continue;
            }
            for (GraphEdge edge : adjacency.get(node)) {
                double candidate = dist[node] + edge.length();
                if (candidate < dist[edge.to()]) {
                    dist[edge.to()] = candidate;
                    queue.add(new double[] {edge.to(), candidate});
                }
            }
        }
        return dist;
    }

    /** Узлы, достижимые из депо: проверка связности планировки (FR-M15-07). */
    public BitSet reachableFromDepot() {
        List<List<GraphEdge>> adjacency = adjacency();
        BitSet seen = new BitSet(nodes.size());
        Deque<Integer> queue = new ArrayDeque<>();
        seen.set(0);
        queue.add(0);
        while (!queue.isEmpty()) {
            for (GraphEdge edge : adjacency.get(queue.poll())) {
                if (!seen.get(edge.to())) {
                    seen.set(edge.to());
                    queue.add(edge.to());
                }
            }
        }
        return seen;
    }
}

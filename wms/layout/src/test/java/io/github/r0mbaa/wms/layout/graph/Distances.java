package io.github.r0mbaa.wms.layout.graph;

import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Дейкстра для проверок в тестах. Рабочая матрица расстояний строится в {@code planner-engine};
 * здесь нужен только независимый способ измерить путь в графе.
 */
final class Distances {

    private Distances() {
    }

    static double[] from(WarehouseGraph graph, int source) {
        List<List<GraphEdge>> adjacency = graph.adjacency();
        double[] dist = new double[graph.nodes().size()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[1], b[1]));
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
}

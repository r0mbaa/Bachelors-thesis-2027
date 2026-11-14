package io.github.r0mbaa.wms.layout.graph;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackProfile;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.layout.model.Rect;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Построение графа склада из планировки (FR-M1-07, FR-M15-06). Решения по задачам §8.7:
 *
 * <ul>
 *   <li><b>А1.</b> Проход не шире {@code w_merge} даёт одну линию точек отбора на его оси, общую
 *       для обеих стен. Шире — у каждой стены своя линия на расстоянии {@code w_merge / 2}.</li>
 *   <li><b>А2.</b> Вдоль прохода точки ставятся на сетку отметок с шагом {@code d_mark} (см.
 *       {@code markStep}). Сетка отсчитывается от начала координат, поэтому отметки
 *       противоположных стен совпадают. Погрешность не больше {@code d_mark / 2}.</li>
 *   <li><b>А3.</b> Секция не шире {@code w_merge} даёт одну точку на все свои ячейки, более широкая
 *       — по точке на ячейку.</li>
 *   <li><b>Б1, Б3, Б5.</b> Проходимость не выводится из регулярности планировки, а считается по
 *       факту: ходить можно везде, где до стеллажа не меньше {@code w_min / 2}.</li>
 *   <li><b>Д1, Д2.</b> Граф разреженный, рёбра только вдоль осей, координаты точные.</li>
 * </ul>
 *
 * <p>Кандидаты в оси движения — линии через точки отбора и депо и средние линии всех
 * «полос» между соседними краями стеллажей. Для прямоугольных препятствий такие линии
 * содержат кратчайший путь по осям, поэтому длины путей в графе не зависят от формы склада.
 * Узлы — точки отбора, депо и пересечения линий в свободном пространстве. Рёбра соединяют
 * соседние узлы на линии, если между ними нет стеллажа. Тупиковые пересечения и
 * промежуточные узлы на прямом отрезке затем удаляются: граф остаётся разреженным (§8.7, Д1).
 */
public final class GraphBuilder {

    private static final double EPS = 1e-6;
    private static final double MM = 1000;

    private GraphBuilder() {
    }

    public static WarehouseGraph build(Layout layout) {
        return build(layout, GraphSettings.DEFAULT);
    }

    public static WarehouseGraph build(Layout layout, GraphSettings settings) {
        return new Construction(layout, settings).run();
    }

    private static final class Construction {

        private final Layout layout;
        private final GraphSettings settings;
        private final List<Rect> racks;
        private final List<Rect> inflated;
        private final double markStep;

        private final Map<Key, Proto> nodes = new LinkedHashMap<>();
        private final Map<LocationCode, Key> cellPoints = new HashMap<>();
        private final TreeSet<Long> verticalCoords = new TreeSet<>();
        private final TreeSet<Long> horizontalCoords = new TreeSet<>();
        private final Map<Key, Map<Key, Double>> adjacency = new HashMap<>();

        Construction(Layout layout, GraphSettings settings) {
            this.layout = layout;
            this.settings = settings;
            this.racks = layout.rows().stream().map(layout::footprint).toList();
            this.inflated = racks.stream().map(r -> r.expand(settings.clearance())).toList();
            this.markStep = markStep(layout, settings);
        }

        WarehouseGraph run() {
            Key depot = Key.of(layout.depot());
            nodes.put(depot, new Proto(NodeKind.DEPOT, depot.point()));
            verticalCoords.add(depot.x());
            horizontalCoords.add(depot.y());
            for (int i = 0; i < layout.rows().size(); i++) {
                addPickPoints(i);
            }
            addSlabMidlines(verticalCoords, true);
            addSlabMidlines(horizontalCoords, false);

            Map<Long, Line> verticals = lines(verticalCoords, true);
            Map<Long, Line> horizontals = lines(horizontalCoords, false);
            for (Line v : verticals.values()) {
                for (Line h : horizontals.values()) {
                    if (v.isFree(h.coord) && h.isFree(v.coord)) {
                        Key key = new Key(v.key, h.key);
                        nodes.putIfAbsent(key, new Proto(NodeKind.INTERSECTION, key.point()));
                    }
                }
            }
            for (Map.Entry<Key, Proto> node : nodes.entrySet()) {
                Line v = verticals.get(node.getKey().x());
                if (v != null && v.isFree(node.getValue().position.y())) {
                    v.members.add(node.getKey());
                }
                Line h = horizontals.get(node.getKey().y());
                if (h != null && h.isFree(node.getValue().position.x())) {
                    h.members.add(node.getKey());
                }
            }
            nodes.keySet().forEach(key -> adjacency.put(key, new HashMap<>()));
            verticals.values().forEach(this::connect);
            horizontals.values().forEach(this::connect);
            prune();
            return assemble();
        }

        /**
         * Точки отбора ряда. Точка лежит перед лицевой стороной, на оси прохода или у стены (А1),
         * вдоль ряда — на ближайшей отметке (А2), одна на секцию или на ячейку (А3).
         */
        private void addPickPoints(int rowIndex) {
            RackRow row = layout.rows().get(rowIndex);
            double depth = layout.rowDepth(row);
            Face face = Face.of(row, depth);
            double sectionStart = 0;
            for (int s = 0; s < row.sections().size(); s++) {
                RackProfile profile = layout.profile(row.sections().get(s));
                boolean singlePoint = profile.sectionWidth() <= settings.mergeWidth() + EPS;
                for (int position = 1; position <= profile.cellsPerLevel(); position++) {
                    double along = face.alongOrigin + sectionStart + (singlePoint
                            ? profile.sectionWidth() / 2
                            : (position - 0.5) * profile.cellWidth());
                    double gap = gapInFront(rowIndex, face, along);
                    double offset = gap <= settings.mergeWidth() + EPS ? gap / 2 : settings.mergeWidth() / 2;
                    double across = face.coord + face.direction * offset;
                    double mark = Math.round(along / markStep) * markStep;
                    Key key = Key.of(face.runsAlongX ? new Point(mark, across) : new Point(across, mark));
                    nodes.putIfAbsent(key, new Proto(NodeKind.PICK_POINT, key.point()));
                    (face.runsAlongX ? horizontalCoords : verticalCoords).add(face.runsAlongX ? key.y() : key.x());
                    for (int level = 1; level <= profile.levels(); level++) {
                        cellPoints.put(new LocationCode(layout.warehouseCode(), row.number(), s + 1, level, position),
                                key);
                    }
                }
                sectionStart += profile.sectionWidth();
            }
        }

        /** Свободное расстояние перед лицевой стороной до ближайшего стеллажа или стены. */
        private double gapInFront(int rowIndex, Face face, double along) {
            double gap = face.direction > 0
                    ? (face.runsAlongX ? layout.length() : layout.width()) - face.coord
                    : face.coord;
            for (int i = 0; i < racks.size(); i++) {
                if (i == rowIndex) {
                    continue;
                }
                Rect r = racks.get(i);
                double lo = face.runsAlongX ? r.minX() : r.minY();
                double hi = face.runsAlongX ? r.maxX() : r.maxY();
                if (along <= lo + EPS || along >= hi - EPS) {
                    continue;
                }
                double near = face.direction > 0
                        ? (face.runsAlongX ? r.minY() : r.minX()) - face.coord
                        : face.coord - (face.runsAlongX ? r.maxY() : r.maxX());
                if (near >= -EPS) {
                    gap = Math.min(gap, Math.max(near, 0));
                }
            }
            return gap;
        }

        /** Средние линии полос между соседними краями стеллажей и стенами (Б1, Б3). */
        private void addSlabMidlines(TreeSet<Long> coords, boolean vertical) {
            TreeSet<Double> edges = new TreeSet<>();
            double max = vertical ? layout.width() : layout.length();
            edges.add(0.0);
            edges.add(max);
            for (Rect r : racks) {
                edges.add(clamp(vertical ? r.minX() : r.minY(), max));
                edges.add(clamp(vertical ? r.maxX() : r.maxY(), max));
            }
            Double previous = null;
            for (double edge : edges) {
                if (previous != null && edge - previous > EPS) {
                    coords.add(mm((previous + edge) / 2));
                }
                previous = edge;
            }
        }

        private Map<Long, Line> lines(TreeSet<Long> coords, boolean vertical) {
            Map<Long, Line> lines = new TreeMap<>();
            for (long key : coords) {
                double coord = key / MM;
                List<double[]> blocked = new ArrayList<>();
                for (Rect r : inflated) {
                    double lo = vertical ? r.minX() : r.minY();
                    double hi = vertical ? r.maxX() : r.maxY();
                    if (lo < coord - EPS && coord + EPS < hi) {
                        blocked.add(vertical ? new double[] {r.minY(), r.maxY()} : new double[] {r.minX(), r.maxX()});
                    }
                }
                lines.put(key, new Line(key, coord, vertical,
                        free(blocked, vertical ? layout.length() : layout.width())));
            }
            return lines;
        }

        /** Свободные отрезки линии: {@code [0, max]} без открытых интервалов стеллажей. */
        private static List<double[]> free(List<double[]> blocked, double max) {
            blocked.sort(Comparator.comparingDouble(b -> b[0]));
            List<double[]> free = new ArrayList<>();
            double start = 0;
            for (double[] b : blocked) {
                if (b[0] > start + EPS) {
                    free.add(new double[] {start, Math.min(b[0], max)});
                }
                start = Math.max(start, b[1]);
            }
            if (start < max - EPS) {
                free.add(new double[] {start, max});
            }
            return free;
        }

        /** Соседние узлы на линии соединяются, если лежат в одном свободном отрезке. */
        private void connect(Line line) {
            line.members.sort(Comparator.comparingLong(k -> line.vertical ? k.y() : k.x()));
            for (int i = 1; i < line.members.size(); i++) {
                Key a = line.members.get(i - 1);
                Key b = line.members.get(i);
                double ta = along(a, line.vertical);
                double tb = along(b, line.vertical);
                if (line.segmentOf(ta) == line.segmentOf(tb)) {
                    link(a, b, tb - ta);
                }
            }
        }

        private double along(Key key, boolean vertical) {
            Point p = nodes.get(key).position;
            return vertical ? p.y() : p.x();
        }

        private void link(Key a, Key b, double length) {
            adjacency.get(a).merge(b, length, Math::min);
            adjacency.get(b).merge(a, length, Math::min);
        }

        /**
         * Удаляет тупиковые пересечения и промежуточные узлы на прямых отрезках. Повороты
         * остаются: по ним маршрут разворачивается в ломаную для схемы (FR-M8-05).
         */
        private void prune() {
            Deque<Key> work = new ArrayDeque<>(nodes.keySet());
            while (!work.isEmpty()) {
                Key key = work.poll();
                Proto node = nodes.get(key);
                if (node == null || node.kind != NodeKind.INTERSECTION) {
                    continue;
                }
                Map<Key, Double> neighbours = adjacency.get(key);
                if (neighbours.size() <= 1) {
                    remove(key, work);
                } else if (neighbours.size() == 2) {
                    List<Key> ends = List.copyOf(neighbours.keySet());
                    Key a = ends.get(0);
                    Key b = ends.get(1);
                    boolean straight = a.x() == key.x() && b.x() == key.x() || a.y() == key.y() && b.y() == key.y();
                    if (straight) {
                        double length = neighbours.get(a) + neighbours.get(b);
                        remove(key, work);
                        link(a, b, length);
                    }
                }
            }
        }

        private void remove(Key key, Deque<Key> work) {
            for (Key neighbour : adjacency.remove(key).keySet()) {
                adjacency.get(neighbour).remove(key);
                work.add(neighbour);
            }
            nodes.remove(key);
        }

        private WarehouseGraph assemble() {
            Comparator<Map.Entry<Key, Proto>> order = Comparator
                    .comparing((Map.Entry<Key, Proto> e) -> e.getValue().kind)
                    .thenComparingLong(e -> e.getKey().x())
                    .thenComparingLong(e -> e.getKey().y());
            List<Map.Entry<Key, Proto>> sorted = nodes.entrySet().stream().sorted(order).toList();
            Map<Key, Integer> ids = new HashMap<>();
            List<GraphNode> graphNodes = new ArrayList<>(sorted.size());
            for (Map.Entry<Key, Proto> entry : sorted) {
                ids.put(entry.getKey(), graphNodes.size());
                graphNodes.add(new GraphNode(graphNodes.size(), entry.getValue().kind, entry.getValue().position));
            }
            List<GraphEdge> edges = new ArrayList<>();
            adjacency.forEach((a, neighbours) -> neighbours.forEach((b, length) -> {
                int from = ids.get(a);
                int to = ids.get(b);
                if (from < to) {
                    edges.add(new GraphEdge(from, to, length));
                }
            }));
            edges.sort(Comparator.comparingInt(GraphEdge::from).thenComparingInt(GraphEdge::to));
            Map<LocationCode, Integer> pickPoints = new HashMap<>();
            cellPoints.forEach((code, key) -> pickPoints.put(code, ids.get(key)));
            return new WarehouseGraph(layout.version(), graphNodes, edges, pickPoints, markStep, settings);
        }

        /**
         * Шаг отметок {@code d_mark}. §8.7 А2 берёт наименьшую ширину секции, но тогда широкая
         * секция, разбитая по ячейкам (А3), сжалась бы обратно в одну-две отметки. Поэтому шаг —
         * наименьшее расстояние между соседними точками: ширина секции для узких секций и
         * ширина ячейки для широких. На складе из одних полок это та же ширина секции.
         */
        private static double markStep(Layout layout, GraphSettings settings) {
            return layout.rows().stream().flatMap(r -> r.sections().stream())
                    .map(layout::profile)
                    .mapToDouble(p -> p.sectionWidth() <= settings.mergeWidth() + EPS ? p.sectionWidth() : p.cellWidth())
                    .min()
                    .orElse(settings.mergeWidth());
        }

        private static double clamp(double value, double max) {
            return Math.max(0, Math.min(max, value));
        }
    }

    private static long mm(double value) {
        return Math.round(value * MM);
    }

    /**
     * Ключ узла с точностью до миллиметра: совпавшие точки двух стен становятся одним узлом.
     * Координаты всех узлов приводятся к этой сетке, поэтому узлы на одной оси имеют в точности
     * равную координату.
     */
    private record Key(long x, long y) {

        static Key of(Point p) {
            return new Key(mm(p.x()), mm(p.y()));
        }

        Point point() {
            return new Point(x / MM, y / MM);
        }
    }

    private record Proto(NodeKind kind, Point position) {
    }

    /** Лицевая линия ряда: где она проходит, куда смотрит и откуда отсчитывается вдоль ряда. */
    private record Face(boolean runsAlongX, double coord, int direction, double alongOrigin) {

        static Face of(RackRow row, double depth) {
            double x = row.origin().x();
            double y = row.origin().y();
            Facing facing = row.facing();
            return switch (facing) {
                case NORTH -> new Face(true, y + depth, 1, x);
                case SOUTH -> new Face(true, y, -1, x);
                case EAST -> new Face(false, x + depth, 1, y);
                case WEST -> new Face(false, x, -1, y);
            };
        }
    }

    /** Кандидат в ось движения и его свободные отрезки. */
    private static final class Line {

        final long key;
        final double coord;
        final boolean vertical;
        final List<double[]> free;
        final List<Key> members = new ArrayList<>();

        Line(long key, double coord, boolean vertical, List<double[]> free) {
            this.key = key;
            this.coord = coord;
            this.vertical = vertical;
            this.free = free;
        }

        boolean isFree(double t) {
            return segmentOf(t) >= 0;
        }

        int segmentOf(double t) {
            for (int i = 0; i < free.size(); i++) {
                if (t >= free.get(i)[0] - EPS && t <= free.get(i)[1] + EPS) {
                    return i;
                }
            }
            return -1;
        }
    }
}

package io.github.r0mbaa.wms.layout.classification;

import io.github.r0mbaa.wms.layout.graph.GraphSettings;
import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.layout.model.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Классификатор планировки (FR-M15-08, §8.7 Г1–Г2). Проверяет признаки регулярной структуры:
 * все ряды параллельны, проходы одинаковы во всех блоках, шаг между проходами постоянен, длины
 * проходов равны, поперечные проходы сквозные, депо лежит на поперечном проходе. Сравнение
 * идёт с относительным допуском (по умолчанию 2 %, Г2): от его величины зависит, какие
 * планировки попадут в класс с точным алгоритмом, поэтому он фиксируется как параметр работы.
 *
 * <p>Классификатор влияет только на применимость эвристик, а не на расстояния: граф строится
 * по фактической проходимости независимо от класса (§8.7, Б3).
 */
public final class LayoutClassifier {

    public static final double DEFAULT_TOLERANCE = 0.02;

    private static final double EPS = 1e-6;
    /** Оси проходов ближе сантиметра — одна ось: две стены одного прохода. */
    private static final double SAME_AXIS = 0.01;

    private LayoutClassifier() {
    }

    public static Classification classify(Layout layout) {
        return classify(layout, DEFAULT_TOLERANCE, GraphSettings.DEFAULT.minAisleWidth());
    }

    /**
     * @param tolerance     относительный допуск сравнения длин и шагов (Г2)
     * @param minAisleWidth {@code w_min}: поперечный проход уже этого — не проход
     */
    public static Classification classify(Layout layout, double tolerance, double minAisleWidth) {
        if (layout.rows().isEmpty()) {
            return general(0, 0, List.of("на плане нет стеллажей"));
        }
        boolean alongX = layout.rows().getFirst().facing().runsAlongX();
        if (layout.rows().stream().anyMatch(r -> r.facing().runsAlongX() != alongX)) {
            return general(0, 0, List.of("ряды не параллельны: часть рядов повёрнута на 90°"));
        }
        // Далее всё в системе, где проходы идут вдоль оси Y: ряды «север–юг» транспонируются.
        List<Row> rows = layout.rows().stream().map(r -> Row.of(layout, r, alongX)).toList();
        double width = alongX ? layout.length() : layout.width();
        double length = alongX ? layout.width() : layout.length();
        Point depot = alongX ? new Point(layout.depot().y(), layout.depot().x()) : layout.depot();

        List<Block> blocks = blocks(rows);
        List<String> deviations = new ArrayList<>();
        List<List<Double>> axes = blocks.stream().map(b -> aisleAxes(b, width)).toList();
        List<Double> reference = axes.getFirst();
        double pitch = meanStep(reference);

        for (int b = 1; b < axes.size(); b++) {
            if (!sameAxes(reference, axes.get(b), tolerance * (Double.isNaN(pitch) ? width : pitch))) {
                deviations.add("проходы блока " + (b + 1) + " не совпадают с проходами первого блока: "
                        + "ряды смещены или поперечный проход упирается в ряд");
                break;
            }
        }
        for (int i = 1; i < reference.size(); i++) {
            double step = reference.get(i) - reference.get(i - 1);
            if (Math.abs(step - pitch) > tolerance * pitch) {
                deviations.add(String.format(Locale.ROOT,
                        "шаг между проходами непостоянен: %.2f м при среднем %.2f м", step, pitch));
                break;
            }
        }
        double meanLength = rows.stream().mapToDouble(r -> r.rect.height()).average().orElseThrow();
        rows.stream().filter(r -> Math.abs(r.rect.height() - meanLength) > tolerance * meanLength).findFirst()
                .ifPresent(r -> deviations.add(String.format(Locale.ROOT,
                        "длины проходов различаются: ряд %d длиной %.2f м при средней %.2f м",
                        r.number, r.rect.height(), meanLength)));

        List<double[]> crossAisles = crossAisles(blocks, length);
        boolean deadEnd = false;
        if (crossAisles.getFirst()[1] - crossAisles.getFirst()[0] < minAisleWidth - EPS) {
            deadEnd = true;
            deviations.add("нет переднего поперечного прохода: проходы тупиковые (§8.7, Б4)");
        }
        if (crossAisles.getLast()[1] - crossAisles.getLast()[0] < minAisleWidth - EPS) {
            deadEnd = true;
            deviations.add("нет заднего поперечного прохода: проходы тупиковые (§8.7, Б4)");
        }
        for (int i = 1; i < crossAisles.size() - 1; i++) {
            if (crossAisles.get(i)[1] - crossAisles.get(i)[0] < minAisleWidth - EPS) {
                deviations.add("поперечный проход между блоками " + i + " и " + (i + 1) + " уже " + minAisleWidth + " м");
            }
        }
        boolean depotOnCrossAisle = crossAisles.stream()
                .anyMatch(band -> depot.y() >= band[0] - EPS && depot.y() <= band[1] + EPS);
        if (!depotOnCrossAisle) {
            deviations.add("депо не на поперечном проходе");
        }

        double aisleLength = blocks.stream().mapToDouble(b -> b.hi - b.lo).average().orElseThrow();
        LayoutClass layoutClass = !deviations.isEmpty() ? LayoutClass.GENERAL_GRAPH
                : blocks.size() == 1 ? LayoutClass.SINGLE_BLOCK_RECTANGULAR : LayoutClass.MULTI_BLOCK;
        return new Classification(layoutClass, reference.size(), blocks.size(), pitch, aisleLength,
                aisleLength / pitch, deadEnd, deviations);
    }

    /** Ряды, перекрывающиеся вдоль прохода, образуют блок; блоки разделены поперечными проходами. */
    private static List<Block> blocks(List<Row> rows) {
        List<Row> sorted = rows.stream().sorted(Comparator.comparingDouble(r -> r.rect.minY())).toList();
        List<Block> blocks = new ArrayList<>();
        for (Row row : sorted) {
            Block last = blocks.isEmpty() ? null : blocks.getLast();
            if (last != null && row.rect.minY() < last.hi - EPS) {
                last.rows.add(row);
                last.hi = Math.max(last.hi, row.rect.maxY());
            } else {
                Block block = new Block(row.rect.minY(), row.rect.maxY());
                block.rows.add(row);
                blocks.add(block);
            }
        }
        return blocks;
    }

    /** Оси проходов блока: середина свободной полосы перед каждой лицевой стороной. */
    private static List<Double> aisleAxes(Block block, double width) {
        List<Double> axes = new ArrayList<>();
        for (Row row : block.rows) {
            double axis;
            if (row.facesPositive) {
                double end = block.rows.stream().filter(o -> o != row && o.rect.minX() >= row.rect.maxX() - EPS)
                        .mapToDouble(o -> o.rect.minX()).min().orElse(width);
                axis = (row.rect.maxX() + end) / 2;
            } else {
                double start = block.rows.stream().filter(o -> o != row && o.rect.maxX() <= row.rect.minX() + EPS)
                        .mapToDouble(o -> o.rect.maxX()).max().orElse(0);
                axis = (start + row.rect.minX()) / 2;
            }
            if (axes.stream().noneMatch(a -> Math.abs(a - axis) < SAME_AXIS)) {
                axes.add(axis);
            }
        }
        axes.sort(Comparator.naturalOrder());
        return axes;
    }

    private static boolean sameAxes(List<Double> a, List<Double> b, double tolerance) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (Math.abs(a.get(i) - b.get(i)) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static double meanStep(List<Double> axes) {
        return axes.size() < 2 ? Double.NaN : (axes.getLast() - axes.getFirst()) / (axes.size() - 1);
    }

    /** Полосы поперечных проходов: перед первым блоком, между блоками и за последним. */
    private static List<double[]> crossAisles(List<Block> blocks, double length) {
        List<double[]> bands = new ArrayList<>();
        bands.add(new double[] {0, blocks.getFirst().lo});
        for (int i = 1; i < blocks.size(); i++) {
            bands.add(new double[] {blocks.get(i - 1).hi, blocks.get(i).lo});
        }
        bands.add(new double[] {blocks.getLast().hi, length});
        return bands;
    }

    private static Classification general(int aisles, int blocks, List<String> deviations) {
        return new Classification(LayoutClass.GENERAL_GRAPH, aisles, blocks, Double.NaN, Double.NaN, Double.NaN,
                false, deviations);
    }

    /** Ряд в системе «проходы вдоль Y». */
    private record Row(int number, Rect rect, boolean facesPositive) {

        static Row of(Layout layout, RackRow row, boolean transpose) {
            Rect r = layout.footprint(row);
            Rect rect = transpose ? new Rect(r.minY(), r.minX(), r.maxY(), r.maxX()) : r;
            boolean positive = row.facing() == Facing.EAST || row.facing() == Facing.NORTH;
            return new Row(row.number(), rect, positive);
        }
    }

    private static final class Block {

        final double lo;
        double hi;
        final List<Row> rows = new ArrayList<>();

        Block(double lo, double hi) {
            this.lo = lo;
            this.hi = hi;
        }
    }
}

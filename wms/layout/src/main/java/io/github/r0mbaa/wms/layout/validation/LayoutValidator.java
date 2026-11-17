package io.github.r0mbaa.wms.layout.validation;

import io.github.r0mbaa.wms.layout.graph.GraphBuilder;
import io.github.r0mbaa.wms.layout.graph.GraphSettings;
import io.github.r0mbaa.wms.layout.graph.WarehouseGraph;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.layout.model.Rect;
import io.github.r0mbaa.wms.layout.validation.Issue.Severity;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Проверка планировки (FR-M15-07). Собирает все замечания списком с местом на плане, а не
 * останавливается на первом: пользователь исправляет их за один проход по плану.
 *
 * <p>Ширина прохода проверяется геометрически перед каждой лицевой стороной (§8.7, Б2), а
 * связность — по графу склада: точка отбора считается достижимой, только если до неё есть путь
 * по осям с допуском {@code w_min / 2} до стеллажей. Растровая сетка (§8.7, Д1) для этого не
 * нужна: граф строится по фактической проходимости и не зависит от регулярности планировки.
 */
public final class LayoutValidator {

    private static final double EPS = 1e-6;

    private LayoutValidator() {
    }

    public static ValidationReport validate(Layout layout) {
        return validate(layout, GraphSettings.DEFAULT);
    }

    public static ValidationReport validate(Layout layout, GraphSettings settings) {
        List<Issue> issues = new ArrayList<>();
        if (layout.rows().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, IssueType.EMPTY,
                    "На плане нет ни одного стеллажа: добавьте ряды или воспользуйтесь мастером", null, List.of()));
        }
        List<Rect> footprints = layout.rows().stream().map(layout::footprint).toList();
        Rect bounds = layout.bounds();

        for (int i = 0; i < layout.rows().size(); i++) {
            if (!bounds.contains(footprints.get(i), EPS)) {
                int number = layout.rows().get(i).number();
                issues.add(new Issue(Severity.ERROR, IssueType.OUT_OF_BOUNDS, "Ряд " + number
                        + " выходит за границы склада: передвиньте его или увеличьте размеры склада",
                        footprints.get(i), List.of(number)));
            }
            for (int j = i + 1; j < layout.rows().size(); j++) {
                if (footprints.get(i).overlaps(footprints.get(j), EPS)) {
                    int a = layout.rows().get(i).number();
                    int b = layout.rows().get(j).number();
                    issues.add(new Issue(Severity.ERROR, IssueType.OVERLAP, "Ряды " + a + " и " + b
                            + " пересекаются: раздвиньте их", footprints.get(i).intersection(footprints.get(j)),
                            List.of(a, b)));
                }
            }
        }

        Set<Integer> narrow = new HashSet<>();
        for (int i = 0; i < layout.rows().size(); i++) {
            checkAisleWidth(layout, footprints, i, settings).ifPresent(issue -> {
                issues.add(issue);
                narrow.addAll(issue.rows());
            });
        }

        boolean depotBlocked = checkDepot(layout, footprints, bounds, settings, issues);
        if (!depotBlocked && !layout.rows().isEmpty()) {
            checkReachability(layout, footprints, settings, narrow, issues);
        }
        return new ValidationReport(issues);
    }

    /** Полоса глубиной {@code w_min} перед лицевой стороной должна быть свободна и внутри склада. */
    private static Optional<Issue> checkAisleWidth(Layout layout, List<Rect> footprints, int index,
            GraphSettings settings) {
        RackRow row = layout.rows().get(index);
        Rect own = footprints.get(index);
        double need = settings.minAisleWidth();
        Rect zone = switch (row.facing()) {
            case NORTH -> new Rect(own.minX(), own.maxY(), own.maxX(), own.maxY() + need);
            case SOUTH -> new Rect(own.minX(), own.minY() - need, own.maxX(), own.minY());
            case EAST -> new Rect(own.maxX(), own.minY(), own.maxX() + need, own.maxY());
            case WEST -> new Rect(own.minX() - need, own.minY(), own.minX(), own.maxY());
        };
        Rect bounds = layout.bounds();
        boolean violated = false;
        double clearance = need;
        if (!bounds.contains(zone, EPS)) {
            violated = true;
            clearance = Math.max(0, wallGap(row, own, bounds));
        }
        for (int j = 0; j < footprints.size(); j++) {
            Rect other = footprints.get(j);
            if (j != index && zone.overlaps(other, EPS)) {
                violated = true;
                clearance = Math.min(clearance, Math.max(0, frontGap(row, own, other)));
            }
        }
        if (!violated) {
            return Optional.empty();
        }
        return Optional.of(new Issue(Severity.ERROR, IssueType.AISLE_TOO_NARROW, String.format(Locale.ROOT,
                "Перед рядом %d свободно %.2f м, а для тележки нужно не меньше %.2f м: раздвиньте ряды",
                row.number(), clearance, need), zone, List.of(row.number())));
    }

    /** Расстояние от лицевой стороны до стеллажа перед ней. */
    private static double frontGap(RackRow row, Rect own, Rect other) {
        return switch (row.facing()) {
            case NORTH -> other.minY() - own.maxY();
            case SOUTH -> own.minY() - other.maxY();
            case EAST -> other.minX() - own.maxX();
            case WEST -> own.minX() - other.maxX();
        };
    }

    /** Расстояние от лицевой стороны до стены склада, в которую она смотрит. */
    private static double wallGap(RackRow row, Rect own, Rect bounds) {
        return switch (row.facing()) {
            case NORTH -> bounds.maxY() - own.maxY();
            case SOUTH -> own.minY() - bounds.minY();
            case EAST -> bounds.maxX() - own.maxX();
            case WEST -> own.minX() - bounds.minX();
        };
    }

    private static boolean checkDepot(Layout layout, List<Rect> footprints, Rect bounds, GraphSettings settings,
            List<Issue> issues) {
        Point depot = layout.depot();
        Rect spot = new Rect(depot.x(), depot.y(), depot.x(), depot.y());
        if (!bounds.contains(depot, EPS)) {
            issues.add(new Issue(Severity.ERROR, IssueType.DEPOT_BLOCKED,
                    "Депо за границами склада: перенесите его на план", spot, List.of()));
            return true;
        }
        double half = settings.minAisleWidth() / 2;
        for (int i = 0; i < footprints.size(); i++) {
            if (footprints.get(i).expand(half - 1e-3).containsStrictly(depot)) {
                int number = layout.rows().get(i).number();
                issues.add(new Issue(Severity.ERROR, IssueType.DEPOT_BLOCKED, String.format(Locale.ROOT,
                        "Депо внутри ряда %d или ближе %.2f м к нему: перенесите депо в проход", number, half),
                        footprints.get(i), List.of(number)));
                return true;
            }
        }
        return false;
    }

    /** Связность: каждая точка отбора достижима из депо по графу склада. */
    private static void checkReachability(Layout layout, List<Rect> footprints, GraphSettings settings,
            Set<Integer> narrow, List<Issue> issues) {
        WarehouseGraph graph = GraphBuilder.build(layout, settings);
        BitSet reachable = graph.reachableFromDepot();
        for (int i = 0; i < layout.rows().size(); i++) {
            int number = layout.rows().get(i).number();
            if (narrow.contains(number)) {
                continue;
            }
            long unreachable = graph.pickPoints().entrySet().stream()
                    .filter(e -> e.getKey().row() == number)
                    .map(Map.Entry::getValue)
                    .distinct()
                    .filter(id -> !reachable.get(id))
                    .count();
            if (unreachable > 0) {
                issues.add(new Issue(Severity.ERROR, IssueType.UNREACHABLE, "До " + unreachable
                        + " точек отбора ряда " + number + " нельзя дойти от депо: откройте проход к ряду",
                        footprints.get(i), List.of(number)));
            }
        }
    }
}

package io.github.r0mbaa.wms.layout.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.layout.cells.Cell;
import io.github.r0mbaa.wms.layout.cells.CellGenerator;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.StandardProfiles;
import io.github.r0mbaa.wms.layout.wizard.DepotPosition;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutParameters;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutWizard;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Свойства графа на случайных типовых планировках: то, что должно выполняться всегда. */
class GraphProperties {

    private static final double EPS = 1e-6;

    /** Связность (FR-M15-07): при проходах не уже w_min до каждой точки отбора можно дойти. */
    @Property(tries = 200)
    void everyPickPointIsReachableFromDepot(@ForAll("layouts") Layout layout) {
        assertThat(GraphBuilderTest.allPickPointsReachable(GraphBuilder.build(layout))).isTrue();
    }

    @Property(tries = 200)
    void everyCellMapsToNearbyPickPointInFrontOfItsFace(@ForAll("layouts") Layout layout) {
        WarehouseGraph graph = GraphBuilder.build(layout);

        for (Cell cell : CellGenerator.generate(layout)) {
            Point point = graph.pickPointOf(cell.code()).position();
            boolean alongX = cell.facing().runsAlongX();
            double along = alongX ? point.x() - cell.access().x() : point.y() - cell.access().y();
            double across = alongX ? point.y() - cell.access().y() : point.x() - cell.access().x();
            // Отметка не дальше d_mark вдоль ряда (центр секции плюс округление до отметки)...
            assertThat(Math.abs(along)).isLessThanOrEqualTo(graph.markStep() + EPS);
            // ...и перед лицевой стороной, не дальше w_merge / 2.
            double sign = switch (cell.facing()) {
                case NORTH, EAST -> 1;
                case SOUTH, WEST -> -1;
            };
            assertThat(across * sign).isPositive().isLessThanOrEqualTo(graph.settings().mergeWidth() / 2 + EPS);
        }
    }

    /** Путь по осям не короче манхэттенского расстояния: граф не срезает углы через стеллажи. */
    @Property(tries = 200)
    void distanceFromDepotIsAtLeastManhattan(@ForAll("layouts") Layout layout) {
        WarehouseGraph graph = GraphBuilder.build(layout);
        double[] dist = Distances.from(graph, 0);
        Point depot = graph.depot().position();

        for (GraphNode node : graph.nodes()) {
            double manhattan = Math.abs(node.position().x() - depot.x()) + Math.abs(node.position().y() - depot.y());
            assertThat(dist[node.id()]).isGreaterThanOrEqualTo(manhattan - EPS);
        }
    }

    /** Рабочий поиск кратчайших путей совпадает с независимой реализацией в тестах. */
    @Property(tries = 100)
    void libraryDistancesMatchReferenceDijkstra(@ForAll("layouts") Layout layout) {
        WarehouseGraph graph = GraphBuilder.build(layout);

        assertThat(graph.distancesFrom(0)).containsExactly(Distances.from(graph, 0));
    }

    @Provide
    Arbitrary<Layout> layouts() {
        return Combinators.combine(
                        Arbitraries.integers().between(1, 6),
                        Arbitraries.integers().between(2, 4),
                        Arbitraries.integers().between(1, 12),
                        Arbitraries.of(StandardProfiles.ALL),
                        Arbitraries.doubles().between(1.2, 4.0).ofScale(1),
                        Arbitraries.doubles().between(1.2, 4.0).ofScale(1),
                        Arbitraries.of(DepotPosition.class))
                .as((aisles, cross, sections, profile, aisle, crossWidth, depot) -> RectangularLayoutWizard.generate(
                        new RectangularLayoutParameters("WH1", aisles, cross, sections, profile, aisle, crossWidth,
                                depot)));
    }
}

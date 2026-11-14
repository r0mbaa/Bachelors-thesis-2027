package io.github.r0mbaa.wms.layout.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackProfile;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.layout.model.StandardProfiles;
import io.github.r0mbaa.wms.layout.wizard.DepotPosition;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutParameters;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutWizard;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class GraphBuilderTest {

    private static final Offset<Double> EPS = within(1e-6);

    /**
     * Два прохода по 2 м, один блок из 5 секций по 1,2 м, поперечные проходы по 3 м, депо
     * напротив первого прохода. Оси проходов x = 1,6 и 4,8; передний поперечный — y = 1,5,
     * задний — y = 10,5; отметки вдоль проходов y = 3,6 .. 8,4.
     */
    private static Layout twoAisles(double aisleWidth) {
        return RectangularLayoutWizard.generate(new RectangularLayoutParameters("WH1", 2, 2, 5,
                StandardProfiles.SHELF_5, aisleWidth, 3.0, DepotPosition.FRONT_LEFT));
    }

    @Test
    void regularLayoutGivesSparseGraphAlongAisleAxes() {
        WarehouseGraph graph = GraphBuilder.build(twoAisles(2.0));

        // Депо, 2 × 5 точек отбора и три поворота: низ второго прохода и два верха.
        assertThat(graph.nodes()).hasSize(14);
        assertThat(graph.nodes()).filteredOn(n -> n.kind() == NodeKind.PICK_POINT).hasSize(10);
        assertThat(graph.edges()).hasSize(14);
        assertThat(graph.depot().position()).isEqualTo(new Point(1.6, 1.5));
        assertThat(graph.markStep()).isCloseTo(1.2, EPS);
    }

    @Test
    void oppositeWallsOfNarrowAisleShareOnePickPoint() {
        WarehouseGraph graph = GraphBuilder.build(twoAisles(2.0));

        GraphNode left = graph.pickPointOf(new LocationCode("WH1", 1, 1, 1, 1));
        GraphNode right = graph.pickPointOf(new LocationCode("WH1", 2, 1, 5, 3));
        assertThat(left).isEqualTo(right);
        assertThat(left.position().x()).isCloseTo(1.6, EPS);
        assertThat(left.position().y()).isCloseTo(3.6, EPS);
        // Все ярусы и ячейки секции — одна колонка, одна точка (§1.5, А3).
        assertThat(graph.pickPointOf(new LocationCode("WH1", 1, 1, 5, 3))).isEqualTo(left);
    }

    @Test
    void distancesFollowAisleAxesAndCrossAisles() {
        WarehouseGraph graph = GraphBuilder.build(twoAisles(2.0));
        double[] fromDepot = Distances.from(graph, 0);

        assertThat(fromDepot[pick(graph, 1, 1)]).isCloseTo(2.1, EPS);
        // Во второй проход через передний поперечный: 3,2 вбок и 2,1 вверх.
        assertThat(fromDepot[pick(graph, 3, 1)]).isCloseTo(5.3, EPS);
        // Дальняя точка второго прохода ближе снизу (3,2 + 6,9), чем через задний проход.
        assertThat(fromDepot[pick(graph, 3, 5)]).isCloseTo(10.1, EPS);
    }

    @Test
    void wideAisleGetsPickLineAtEachWall() {
        WarehouseGraph graph = GraphBuilder.build(twoAisles(3.0));

        GraphNode left = graph.pickPointOf(new LocationCode("WH1", 1, 1, 1, 1));
        GraphNode right = graph.pickPointOf(new LocationCode("WH1", 2, 1, 1, 1));
        assertThat(left).isNotEqualTo(right);
        // Лицевые линии x = 0,6 и 3,6; линии точек в w_merge / 2 = 1,25 м от них.
        assertThat(left.position().x()).isCloseTo(1.85, EPS);
        assertThat(right.position().x()).isCloseTo(2.35, EPS);
        assertThat(graph.nodes()).filteredOn(n -> n.kind() == NodeKind.PICK_POINT).hasSize(20);
        assertThat(allPickPointsReachable(graph)).isTrue();
    }

    @Test
    void wideSectionIsSplitIntoPointPerCell() {
        Layout layout = RectangularLayoutWizard.generate(new RectangularLayoutParameters("WH1", 1, 2, 2,
                StandardProfiles.PALLET_3, 3.0, 3.0, DepotPosition.FRONT_LEFT));
        WarehouseGraph graph = GraphBuilder.build(layout);

        // Секция 2,7 м шире w_merge: три паллетных места — три точки с шагом 0,9 м.
        assertThat(graph.markStep()).isCloseTo(0.9, EPS);
        assertThat(List.of(1, 2, 3).stream()
                .map(p -> graph.pickPointOf(new LocationCode("WH1", 1, 1, 1, p)).id())
                .distinct()).hasSize(3);
    }

    @Test
    void pickPointsOfTooNarrowAisleAreUnreachable() {
        WarehouseGraph graph = GraphBuilder.build(twoAisles(1.0));

        BitSet reachable = graph.reachableFromDepot();
        assertThat(reachable.get(pick(graph, 1, 1))).isFalse();
        assertThat(reachable.get(pick(graph, 3, 1))).isFalse();
    }

    /**
     * Б3: средний поперечный проход перегорожен между вторым и третьим проходами, и путь
     * строится по фактической проходимости — в обход через передний поперечный.
     */
    @Test
    void blockedCrossAisleForcesDetourThroughFront() {
        Layout regular = RectangularLayoutWizard.generate(new RectangularLayoutParameters("WH1", 3, 3, 5,
                StandardProfiles.SHELF_5, 2.0, 3.0, DepotPosition.FRONT_LEFT));
        List<RackProfile> profiles = List.of(StandardProfiles.SHELF_5, StandardProfiles.FLOOR);
        List<RackRow> rows = new ArrayList<>(regular.rows());
        rows.add(new RackRow(13, new Point(6.0, 10.0), Facing.NORTH,
                List.of(StandardProfiles.FLOOR.name(), StandardProfiles.FLOOR.name())));
        Layout blocked = new Layout("WH1", 0, regular.width(), regular.length(), regular.depot(), profiles, rows);

        int from = pick(GraphBuilder.build(regular), 3, 5);
        int to = pick(GraphBuilder.build(regular), 5, 5);
        assertThat(Distances.from(GraphBuilder.build(regular), from)[to]).isCloseTo(2.1 + 3.2 + 2.1, EPS);

        WarehouseGraph graph = GraphBuilder.build(blocked);
        // Точки (4,8; 8,4) и (8,0; 8,4): вниз 6,9, вбок 3,2, вверх 6,9.
        assertThat(Distances.from(graph, pick(graph, 3, 5))[pick(graph, 5, 5)]).isCloseTo(17.0, EPS);
    }

    @Test
    void edgesRunAlongAxesAndTheirLengthsMatchCoordinates() {
        WarehouseGraph graph = GraphBuilder.build(twoAisles(2.0));

        assertThat(graph.edges()).allSatisfy(edge -> {
            Point a = graph.node(edge.from()).position();
            Point b = graph.node(edge.to()).position();
            assertThat(a.x() == b.x() || a.y() == b.y()).isTrue();
            assertThat(edge.length()).isCloseTo(Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()), EPS);
        });
    }

    private static int pick(WarehouseGraph graph, int row, int section) {
        return graph.pickPointOf(new LocationCode("WH1", row, section, 1, 1)).id();
    }

    static boolean allPickPointsReachable(WarehouseGraph graph) {
        BitSet reachable = graph.reachableFromDepot();
        return graph.nodes().stream()
                .filter(n -> n.kind() == NodeKind.PICK_POINT)
                .allMatch(n -> reachable.get(n.id()));
    }
}

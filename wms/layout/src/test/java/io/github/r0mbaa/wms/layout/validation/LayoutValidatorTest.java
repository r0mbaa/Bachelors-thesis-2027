package io.github.r0mbaa.wms.layout.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.layout.model.StandardProfiles;
import io.github.r0mbaa.wms.layout.wizard.DepotPosition;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutParameters;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutWizard;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class LayoutValidatorTest {

    /** Полочный профиль 5 ярусов: секция 1,2 м, глубина 0,6 м. */
    private static final String S = StandardProfiles.SHELF_5.name();

    @Test
    void emptyPlanIsOnlyWarned() {
        ValidationReport report = LayoutValidator.validate(layout(new Point(1, 1)));

        assertThat(report.hasErrors()).isFalse();
        assertThat(report.issues()).extracting(Issue::type).containsExactly(IssueType.EMPTY);
    }

    @Test
    void overlappingRowsAreReportedWithCommonArea() {
        ValidationReport report = LayoutValidator.validate(layout(new Point(1, 1),
                new RackRow(1, new Point(3, 3), Facing.EAST, List.of(S, S)),
                new RackRow(2, new Point(3.3, 4), Facing.WEST, List.of(S))));

        Issue overlap = issue(report, IssueType.OVERLAP);
        assertThat(overlap.rows()).containsExactly(1, 2);
        assertThat(overlap.area().minX()).isEqualTo(3.3);
        assertThat(overlap.area().maxY()).isEqualTo(5.2);
    }

    @Test
    void rowOutsideWarehouseIsReported() {
        ValidationReport report = LayoutValidator.validate(layout(new Point(1, 1),
                new RackRow(1, new Point(19.8, 3), Facing.WEST, List.of(S))));

        assertThat(issue(report, IssueType.OUT_OF_BOUNDS).rows()).containsExactly(1);
    }

    @Test
    void faceTooCloseToOtherRowOrWallIsNarrowAisle() {
        // Лицевые стороны рядов 1 и 2 в 0,8 м друг от друга, лицо ряда 3 — в 0,5 м от стены.
        ValidationReport report = LayoutValidator.validate(layout(new Point(1, 1),
                new RackRow(1, new Point(3, 3), Facing.EAST, List.of(S)),
                new RackRow(2, new Point(4.4, 3), Facing.WEST, List.of(S)),
                new RackRow(3, new Point(18.9, 3), Facing.EAST, List.of(S))));

        assertThat(report.issues()).filteredOn(i -> i.type() == IssueType.AISLE_TOO_NARROW)
                .extracting(i -> i.rows().getFirst()).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(report.issues()).filteredOn(i -> i.rows().contains(1)).extracting(Issue::message)
                .anySatisfy(m -> assertThat(m).contains("свободно 0.80 м", "не меньше 1.20 м"));
        assertThat(report.issues()).filteredOn(i -> i.rows().contains(3)).extracting(Issue::message)
                .anySatisfy(m -> assertThat(m).contains("свободно 0.50 м"));
        // Узкие ряды не дублируются замечанием о недостижимости.
        assertThat(report.issues()).noneMatch(i -> i.type() == IssueType.UNREACHABLE);
    }

    @Test
    void depotInsideOrNextToRowIsReported() {
        ValidationReport report = LayoutValidator.validate(layout(new Point(3.3, 3.5),
                new RackRow(1, new Point(3, 3), Facing.EAST, List.of(S))));

        assertThat(issue(report, IssueType.DEPOT_BLOCKED).rows()).containsExactly(1);
    }

    /** Проход между рядами 1 и 2 со всех сторон закрыт рядами 3 и 4: к нему не подойти. */
    @Test
    void enclosedAisleIsUnreachable() {
        ValidationReport report = LayoutValidator.validate(layout(new Point(1, 1),
                new RackRow(1, new Point(4.0, 2.0), Facing.EAST, List.of(S, S, S, S, S)),
                new RackRow(2, new Point(6.6, 2.0), Facing.WEST, List.of(S, S, S, S, S)),
                new RackRow(3, new Point(3.8, 8.0), Facing.NORTH, List.of(S, S, S)),
                new RackRow(4, new Point(3.8, 1.4), Facing.SOUTH, List.of(S, S, S))));

        assertThat(report.issues()).filteredOn(i -> i.type() == IssueType.UNREACHABLE)
                .extracting(i -> i.rows().getFirst()).containsExactlyInAnyOrder(1, 2);
        assertThat(report.issues()).extracting(Issue::type).doesNotContain(IssueType.AISLE_TOO_NARROW,
                IssueType.OVERLAP);
    }

    @Property(tries = 200)
    void wizardLayoutsWithWideEnoughAislesAreValid(@ForAll("layouts") Layout layout) {
        assertThat(LayoutValidator.validate(layout).issues()).isEmpty();
    }

    @Test
    void wizardLayoutWithNarrowAislesIsRejected() {
        Layout layout = RectangularLayoutWizard.generate(new RectangularLayoutParameters("WH1", 2, 2, 5,
                StandardProfiles.SHELF_5, 1.0, 3.0, DepotPosition.FRONT_LEFT));

        assertThat(LayoutValidator.validate(layout).errors()).extracting(Issue::type)
                .containsOnly(IssueType.AISLE_TOO_NARROW).hasSize(4);
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

    private static Layout layout(Point depot, RackRow... rows) {
        return new Layout("WH1", 0, 20, 10, depot, List.of(StandardProfiles.SHELF_5), List.of(rows));
    }

    private static Issue issue(ValidationReport report, IssueType type) {
        return report.issues().stream().filter(i -> i.type() == type).findFirst().orElseThrow();
    }
}

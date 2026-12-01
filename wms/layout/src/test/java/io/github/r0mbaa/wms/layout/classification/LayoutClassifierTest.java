package io.github.r0mbaa.wms.layout.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.layout.model.StandardProfiles;
import io.github.r0mbaa.wms.layout.wizard.DepotPosition;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutParameters;
import io.github.r0mbaa.wms.layout.wizard.RectangularLayoutWizard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class LayoutClassifierTest {

    /** 3 прохода по 2 м, 10 секций по 1,2 м, поперечные по 3 м: d = 3,2 м, L = 12 м. */
    private static Layout wizard(int crossAisles) {
        return RectangularLayoutWizard.generate(new RectangularLayoutParameters("WH1", 3, crossAisles, 10,
                StandardProfiles.SHELF_5, 2.0, 3.0, DepotPosition.FRONT_LEFT));
    }

    @Test
    void singleBlockWizardLayoutIsRatliffRosenthalCase() {
        Classification c = LayoutClassifier.classify(wizard(2));

        assertThat(c.layoutClass()).isEqualTo(LayoutClass.SINGLE_BLOCK_RECTANGULAR);
        assertThat(c.deviations()).isEmpty();
        assertThat(c.aisles()).isEqualTo(3);
        assertThat(c.aislePitch()).isCloseTo(3.2, within(1e-9));
        assertThat(c.aisleLength()).isCloseTo(12.0, within(1e-9));
        assertThat(c.shapeFactor()).isCloseTo(3.75, within(1e-9));
    }

    @Test
    void severalBlocksMakeMultiBlockLayout() {
        Classification c = LayoutClassifier.classify(wizard(4));

        assertThat(c.layoutClass()).isEqualTo(LayoutClass.MULTI_BLOCK);
        assertThat(c.blocks()).isEqualTo(3);
    }

    @Test
    void rotatedLayoutIsClassifiedTheSame() {
        Layout rotated = rotate(wizard(2));

        assertThat(LayoutClassifier.classify(rotated).layoutClass()).isEqualTo(LayoutClass.SINGLE_BLOCK_RECTANGULAR);
    }

    @Test
    void depotInsideAisleIsNotOnCrossAisle() {
        Layout base = wizard(2);
        Layout moved = new Layout(base.warehouseCode(), 0, base.width(), base.length(), new Point(1.6, 9.0),
                base.profiles(), base.rows());

        Classification c = LayoutClassifier.classify(moved);
        assertThat(c.layoutClass()).isEqualTo(LayoutClass.GENERAL_GRAPH);
        assertThat(c.deviations()).anySatisfy(d -> assertThat(d).contains("депо не на поперечном проходе"));
    }

    @Test
    void shorterRowBreaksEqualAisleLengths() {
        Layout c = replaceRow(wizard(2), 3, row -> new RackRow(row.number(), row.origin(), row.facing(),
                row.sections().subList(0, 9)));

        assertThat(LayoutClassifier.classify(c).deviations()).anySatisfy(d -> assertThat(d).contains("длины проходов"));
    }

    /** Г2: смещение ряда на 3 см (меньше 2 % шага) не выводит планировку из класса, на 20 см — выводит. */
    @Test
    void smallDeviationsAreToleratedAndLargeOnesAreNot() {
        assertThat(LayoutClassifier.classify(shiftColumn(wizard(2), 0.03)).layoutClass())
                .isEqualTo(LayoutClass.SINGLE_BLOCK_RECTANGULAR);
        assertThat(LayoutClassifier.classify(shiftColumn(wizard(2), 0.20)).deviations())
                .anySatisfy(d -> assertThat(d).contains("шаг между проходами непостоянен"));
    }

    @Test
    void missingBackCrossAisleMeansDeadEndAisles() {
        Layout base = wizard(2);
        Layout noBack = new Layout(base.warehouseCode(), 0, base.width(), base.length() - 2.5, base.depot(),
                base.profiles(), base.rows());

        Classification c = LayoutClassifier.classify(noBack);
        assertThat(c.layoutClass()).isEqualTo(LayoutClass.GENERAL_GRAPH);
        assertThat(c.deadEndAisles()).isTrue();
    }

    @Test
    void mixedOrientationIsGeneralGraph() {
        Layout mixed = replaceRow(wizard(2), 1, row -> new RackRow(row.number(), row.origin(), Facing.NORTH,
                row.sections().subList(0, 1)));

        assertThat(LayoutClassifier.classify(mixed).deviations()).containsExactly(
                "ряды не параллельны: часть рядов повёрнута на 90°");
    }

    @Property(tries = 200)
    void everyWizardLayoutIsRegular(@ForAll("layouts") Layout layout) {
        Classification c = LayoutClassifier.classify(layout);

        assertThat(c.deviations()).isEmpty();
        assertThat(c.layoutClass()).isIn(LayoutClass.SINGLE_BLOCK_RECTANGULAR, LayoutClass.MULTI_BLOCK);
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

    /** Поворот склада на 90°: ряды «восток–запад» становятся рядами «север–юг». */
    private static Layout rotate(Layout layout) {
        List<RackRow> rows = layout.rows().stream()
                .map(r -> new RackRow(r.number(), new Point(r.origin().y(), r.origin().x()),
                        r.facing() == Facing.EAST ? Facing.NORTH : Facing.SOUTH, r.sections()))
                .toList();
        return new Layout(layout.warehouseCode(), 0, layout.length(), layout.width(),
                new Point(layout.depot().y(), layout.depot().x()), layout.profiles(), rows);
    }

    /** Сдвиг правой пары рядов третьего прохода вдоль оси X. */
    private static Layout shiftColumn(Layout layout, double dx) {
        Layout wider = new Layout(layout.warehouseCode(), 0, layout.width() + 1, layout.length(), layout.depot(),
                layout.profiles(), layout.rows());
        UnaryOperator<RackRow> shift = r -> new RackRow(r.number(), new Point(r.origin().x() + dx, r.origin().y()),
                r.facing(), r.sections());
        return replaceRow(replaceRow(wider, 5, shift), 6, shift);
    }

    private static Layout replaceRow(Layout layout, int number, UnaryOperator<RackRow> change) {
        List<RackRow> rows = new ArrayList<>();
        for (RackRow row : layout.rows()) {
            rows.add(row.number() == number ? change.apply(row) : row);
        }
        return new Layout(layout.warehouseCode(), 0, layout.width(), layout.length(), layout.depot(),
                layout.profiles(), rows);
    }
}

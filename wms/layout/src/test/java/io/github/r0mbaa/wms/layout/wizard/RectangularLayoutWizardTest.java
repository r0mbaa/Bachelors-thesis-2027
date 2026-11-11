package io.github.r0mbaa.wms.layout.wizard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.r0mbaa.wms.layout.cells.Cell;
import io.github.r0mbaa.wms.layout.cells.CellGenerator;
import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.layout.model.StandardProfiles;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class RectangularLayoutWizardTest {

    private static final Offset<Double> EPS = within(1e-9);

    /** 3 прохода, 2 блока, по 10 секций полочного 5-ярусного (1,2 м), проход 2 м, поперечный 3 м. */
    private final RectangularLayoutParameters params = new RectangularLayoutParameters(
            "WH1", 3, 3, 10, StandardProfiles.SHELF_5, 2.0, 3.0, DepotPosition.FRONT_LEFT);

    @Test
    void derivedDimensionsFollowSection81() {
        // d = w_a + 2·w_r = 2 + 2·0,6; L = 10 × 1,2.
        assertThat(params.aislePitch()).isCloseTo(3.2, EPS);
        assertThat(params.blockLength()).isCloseTo(12.0, EPS);
        assertThat(params.shapeFactor()).isCloseTo(12.0 / 3.2, EPS);
        assertThat(params.width()).isCloseTo(9.6, EPS);
        assertThat(params.length()).isCloseTo(3 * 3.0 + 2 * 12.0, EPS);
    }

    @Test
    void rowsFaceTheirAislesAndStandBackToBackBetweenAisles() {
        Layout layout = RectangularLayoutWizard.generate(params);

        assertThat(layout.rows()).hasSize(2 * 3 * 2);
        assertThat(layout.rows()).extracting(RackRow::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        RackRow firstRight = layout.rows().get(1);
        RackRow secondLeft = layout.rows().get(2);
        assertThat(layout.rows().get(0).facing()).isEqualTo(Facing.EAST);
        assertThat(firstRight.facing()).isEqualTo(Facing.WEST);
        // Правый ряд первого прохода и левый второго стоят вплотную спинами.
        assertThat(firstRight.origin().x() + StandardProfiles.SHELF_5.depth()).isCloseTo(secondLeft.origin().x(), EPS);
        // Второй блок начинается после поперечного прохода.
        assertThat(layout.rows().get(6).origin().y()).isCloseTo(3.0 + 12.0 + 3.0, EPS);
    }

    @Test
    void cellsOfOppositeRowsFaceTheSameAisle() {
        Layout layout = RectangularLayoutWizard.generate(params);
        List<Cell> cells = CellGenerator.generate(layout);

        // Лицевые линии рядов 1 и 2 — границы первого прохода шириной 2 м.
        assertThat(cells).filteredOn(c -> c.code().row() == 1)
                .allSatisfy(c -> assertThat(c.access().x()).isCloseTo(0.6, EPS));
        assertThat(cells).filteredOn(c -> c.code().row() == 2)
                .allSatisfy(c -> assertThat(c.access().x()).isCloseTo(2.6, EPS));
        assertThat(cells).hasSize(12 * 10 * 5 * 3);
    }

    @Test
    void depotStandsOnFrontCrossAisleAxis() {
        assertThat(RectangularLayoutWizard.generate(params).depot().x()).isCloseTo(1.6, EPS);
        assertThat(RectangularLayoutWizard.generate(params).depot().y()).isCloseTo(1.5, EPS);
        assertThat(RectangularLayoutWizard.generate(withDepot(DepotPosition.FRONT_RIGHT)).depot().x())
                .isCloseTo(1.6 + 2 * 3.2, EPS);
        assertThat(RectangularLayoutWizard.generate(withDepot(DepotPosition.FRONT_CENTER)).depot().x())
                .isCloseTo(4.8, EPS);
    }

    @Test
    void rowNumbersMustFitIntoCellAddress() {
        assertThatThrownBy(() -> new RectangularLayoutParameters("WH1", 25, 3, 10, StandardProfiles.SHELF_5, 2.0,
                3.0, DepotPosition.FRONT_LEFT))
                .hasMessageContaining("100 рядов");
        assertThatThrownBy(() -> new RectangularLayoutParameters("WH1", 3, 1, 10, StandardProfiles.SHELF_5, 2.0,
                3.0, DepotPosition.FRONT_LEFT))
                .hasMessageContaining("не меньше двух");
    }

    private RectangularLayoutParameters withDepot(DepotPosition depot) {
        return new RectangularLayoutParameters(params.warehouseCode(), params.aisles(), params.crossAisles(),
                params.sectionsPerBlock(), params.profile(), params.aisleWidth(), params.crossAisleWidth(), depot);
    }
}

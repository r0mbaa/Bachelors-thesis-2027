package io.github.r0mbaa.wms.layout.cells;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackKind;
import io.github.r0mbaa.wms.layout.model.RackProfile;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class CellGeneratorTest {

    private static final Offset<Double> EPS = within(1e-9);

    /** 3 яруса × 2 ячейки по 0,6 м, глубина 0,5 м: секция шириной 1,2 м. */
    private static final RackProfile SHELF =
            new RackProfile("S", RackKind.SHELF, List.of(0.5, 0.4, 0.4), 2, 0.6, 0.5, 150);

    /** Паллетный: 2 яруса × 1 ячейка 2,7 м, глубина 1,0 м. */
    private static final RackProfile PALLET =
            new RackProfile("P", RackKind.PALLET, List.of(1.5, 1.5), 1, 2.7, 1.0, 2000);

    private static Layout layoutOf(RackRow... rows) {
        return new Layout("WH1", 1, 50, 30, new Point(0, 0), List.of(SHELF, PALLET), List.of(rows));
    }

    @Test
    void generatesOneCellPerLevelAndPositionWithCodesFromRowNumber() {
        RackRow row = new RackRow(3, new Point(2, 10), Facing.NORTH, List.of("S", "S"));

        List<Cell> cells = CellGenerator.generate(layoutOf(row));

        assertThat(cells).hasSize(2 * 3 * 2);
        assertThat(cells.getFirst().code()).isEqualTo(new LocationCode("WH1", 3, 1, 1, 1));
        assertThat(cells.getLast().code()).isEqualTo(new LocationCode("WH1", 3, 2, 3, 2));
        assertThat(cells).extracting(Cell::code).doesNotHaveDuplicates();
    }

    @Test
    void northFacingRowHasFaceOnItsNorthEdge() {
        RackRow row = new RackRow(1, new Point(2, 10), Facing.NORTH, List.of("S", "S"));

        List<Cell> cells = CellGenerator.generate(layoutOf(row));
        Cell first = cells.getFirst();
        Cell last = cells.getLast();

        assertThat(first.center().x()).isCloseTo(2.3, EPS);
        assertThat(first.center().y()).isCloseTo(10.25, EPS);
        assertThat(first.access().y()).isCloseTo(10.5, EPS);
        // Секция 2, позиция 2: 1,2 м первой секции + 1,5 ячейки по 0,6 м.
        assertThat(last.center().x()).isCloseTo(2 + 1.2 + 0.9, EPS);
        assertThat(last.shelfHeight()).isCloseTo(0.9, EPS);
        // Верхний ярус 0,4 м: 0,6 × 0,5 × 0,4; нагрузка 150 кг на ярус из двух ячеек.
        assertThat(last.volume()).isCloseTo(0.12, EPS);
        assertThat(last.maxWeightKg()).isCloseTo(75, EPS);
    }

    @Test
    void sectionsOfDifferentDepthAreAlignedByFaceLine() {
        RackRow row = new RackRow(1, new Point(0, 0), Facing.SOUTH, List.of("S", "P"));

        List<Cell> cells = CellGenerator.generate(layoutOf(row));

        assertThat(cells).allSatisfy(c -> assertThat(c.access().y()).isCloseTo(0.0, EPS));
        assertThat(cells).filteredOn(c -> c.code().section() == 1)
                .allSatisfy(c -> assertThat(c.center().y()).isCloseTo(0.25, EPS));
        assertThat(cells).filteredOn(c -> c.code().section() == 2)
                .allSatisfy(c -> assertThat(c.center().y()).isCloseTo(0.5, EPS));
    }

    @Test
    void eastAndWestFacingRowsRunAlongY() {
        RackRow east = new RackRow(1, new Point(5, 0), Facing.EAST, List.of("S"));
        RackRow west = new RackRow(2, new Point(8, 0), Facing.WEST, List.of("S"));

        List<Cell> cells = CellGenerator.generate(layoutOf(east, west));

        assertThat(cells).filteredOn(c -> c.code().row() == 1)
                .allSatisfy(c -> assertThat(c.access().x()).isCloseTo(5.5, EPS));
        assertThat(cells).filteredOn(c -> c.code().row() == 2)
                .allSatisfy(c -> assertThat(c.access().x()).isCloseTo(8.0, EPS));
        assertThat(cells.getFirst().center().y()).isCloseTo(0.3, EPS);
    }

    @Test
    void layoutRejectsDuplicateRowNumbersAndUnknownProfiles() {
        RackRow row = new RackRow(1, new Point(0, 0), Facing.NORTH, List.of("S"));
        RackRow sameNumber = new RackRow(1, new Point(0, 5), Facing.NORTH, List.of("S"));
        RackRow unknown = new RackRow(2, new Point(0, 5), Facing.NORTH, List.of("MEZZ"));

        assertThatThrownBy(() -> layoutOf(row, sameNumber)).hasMessageContaining("использован дважды");
        assertThatThrownBy(() -> layoutOf(row, unknown)).hasMessageContaining("неизвестный профиль 'MEZZ'");
    }
}

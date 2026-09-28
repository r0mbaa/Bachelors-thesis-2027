package io.github.r0mbaa.wms.layout.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RackProfileTest {

    /** Нижний ярус выше остальных — под крупногабаритный товар (FR-M15-03b). */
    private final RackProfile profile =
            new RackProfile("Полочный 3", RackKind.SHELF, List.of(0.5, 0.4, 0.4), 3, 0.4, 0.6, 150);

    @Test
    void shelfHeightIsSumOfLevelsBelow() {
        assertThat(profile.shelfHeight(1)).isZero();
        assertThat(profile.shelfHeight(2)).isCloseTo(0.5, within(1e-9));
        assertThat(profile.shelfHeight(3)).isCloseTo(0.9, within(1e-9));
        assertThat(profile.totalHeight()).isCloseTo(1.3, within(1e-9));
    }

    @Test
    void sectionWidthIsCellsTimesCellWidth() {
        assertThat(profile.sectionWidth()).isCloseTo(1.2, within(1e-9));
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThatThrownBy(() -> new RackProfile("X", RackKind.SHELF, List.of(0.4, 0.0), 1, 0.4, 0.6, 100))
                .hasMessageContaining("высота яруса");
        assertThatThrownBy(() -> new RackProfile("X", RackKind.SHELF, List.of(0.4), 1, Double.NaN, 0.6, 100))
                .hasMessageContaining("ширина ячейки");
    }

    @Test
    void rejectsMoreLevelsThanAddressAllows() {
        List<Double> tenLevels = Collections.nCopies(10, 0.3);

        assertThatThrownBy(() -> new RackProfile("X", RackKind.SHELF, tenLevels, 1, 0.4, 0.6, 100))
                .hasMessageContaining("число ярусов");
    }
}

package io.github.r0mbaa.wms.shared.marking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class LocationCodeTest {

    private final LocationCode code = new LocationCode("WH1", 3, 12, 4, 2);

    @Test
    void formatsAddressWithZeroPaddedRowAndSection() {
        assertThat(code.address()).isEqualTo("WH1-R03-12-4-2");
        assertThat(code.value()).startsWith("WH1-R03-12-4-2-").hasSize("WH1-R03-12-4-2-K".length());
    }

    @Test
    void parsesItsOwnValueIgnoringCaseAndSurroundingSpaces() {
        String typed = "  " + code.value().toLowerCase(Locale.ROOT) + " ";

        assertThat(LocationCode.parse(typed)).isEqualTo(code);
    }

    @Test
    void rejectsWrongCheckCharacterWithHintToCompareWithLabel() {
        char wrong = code.checkCharacter() == '0' ? '1' : '0';

        assertThatThrownBy(() -> LocationCode.parse(code.address() + "-" + wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("сверьте код с этикеткой");
    }

    @Test
    void rejectsCodeWithoutCheckCharacter() {
        assertThatThrownBy(() -> LocationCode.parse(code.address()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не соответствует формату");
    }

    @Test
    void rejectsComponentsOutOfRange() {
        assertThatThrownBy(() -> new LocationCode("WH1", 0, 1, 1, 1)).hasMessageContaining("ряда");
        assertThatThrownBy(() -> new LocationCode("WH1", 1, 100, 1, 1)).hasMessageContaining("секции");
        assertThatThrownBy(() -> new LocationCode("WH1", 1, 1, 10, 1)).hasMessageContaining("яруса");
        assertThatThrownBy(() -> new LocationCode("1WH", 1, 1, 1, 1)).hasMessageContaining("склада");
    }
}

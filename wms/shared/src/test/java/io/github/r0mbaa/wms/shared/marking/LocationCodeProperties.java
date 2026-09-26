package io.github.r0mbaa.wms.shared.marking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class LocationCodeProperties {

    @Property
    void parseRestoresAnyFormattedCode(@ForAll("codes") LocationCode code) {
        assertThat(LocationCode.parse(code.value())).isEqualTo(code);
    }

    /** Главное назначение контрольного символа: одна опечатка при ручном вводе не проходит. */
    @Property
    void anySingleCharacterSubstitutionIsRejected(
            @ForAll("codes") LocationCode code,
            @ForAll @IntRange(min = 0, max = 1_000) int positionSeed,
            @ForAll("alphanumeric") char replacement) {
        char[] chars = code.value().toCharArray();
        int[] positions = IntStream.range(0, chars.length).filter(i -> chars[i] != '-').toArray();
        int position = positions[positionSeed % positions.length];
        Assume.that(chars[position] != replacement);
        chars[position] = replacement;

        assertThatThrownBy(() -> LocationCode.parse(new String(chars)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<LocationCode> codes() {
        Arbitrary<String> warehouse = Combinators.combine(
                        Arbitraries.chars().range('A', 'Z'),
                        Arbitraries.strings().withCharRange('A', 'Z').withCharRange('0', '9').ofMaxLength(5))
                .as((first, rest) -> first + rest);
        return Combinators.combine(
                        warehouse,
                        Arbitraries.integers().between(1, LocationCode.MAX_ROW),
                        Arbitraries.integers().between(1, LocationCode.MAX_SECTION),
                        Arbitraries.integers().between(1, LocationCode.MAX_LEVEL),
                        Arbitraries.integers().between(1, LocationCode.MAX_POSITION))
                .as(LocationCode::new);
    }

    @Provide
    Arbitrary<Character> alphanumeric() {
        return Arbitraries.chars().range('A', 'Z').range('0', '9');
    }
}

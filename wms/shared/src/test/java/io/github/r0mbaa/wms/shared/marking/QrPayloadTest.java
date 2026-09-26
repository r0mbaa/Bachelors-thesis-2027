package io.github.r0mbaa.wms.shared.marking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QrPayloadTest {

    @Test
    void recognizesEachEntityTypeByPrefix() {
        LocationCode cell = new LocationCode("WH1", 3, 12, 4, 2);

        assertThat(QrPayload.parse("LOC:" + cell.value())).isEqualTo(new QrPayload.Location(cell));
        assertThat(QrPayload.parse("SKU:85123A")).isEqualTo(new QrPayload.Sku("85123A"));
        assertThat(QrPayload.parse("CNT:T-07")).isEqualTo(new QrPayload.Container("T-07"));
        assertThat(QrPayload.parse("TSK:1042")).isEqualTo(new QrPayload.Task("1042"));
        assertThat(QrPayload.parse("WRK:P15")).isEqualTo(new QrPayload.Worker("P15"));
    }

    @Test
    void encodeAndParseAreInverse() {
        QrPayload payload = new QrPayload.Location(new LocationCode("A", 1, 1, 1, 1));

        assertThat(QrPayload.parse(payload.encode())).isEqualTo(payload);
    }

    @Test
    void prefixIsCaseInsensitive() {
        assertThat(QrPayload.parse("sku:85123A")).isEqualTo(new QrPayload.Sku("85123A"));
    }

    @Test
    void foreignBarcodeIsRejectedWithInstruction() {
        assertThatThrownBy(() -> QrPayload.parse("4601234567890"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("отсканируйте QR с префиксом");
    }

    @Test
    void unknownPrefixIsRejected() {
        assertThatThrownBy(() -> QrPayload.parse("BOX:17"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестный тип этикетки");
    }

    @Test
    void emptyValueIsRejected() {
        assertThatThrownBy(() -> QrPayload.parse("SKU:")).isInstanceOf(IllegalArgumentException.class);
    }
}

package io.github.r0mbaa.wms.core.print;

import java.util.List;

/**
 * Этикетка: QR и строки текста рядом с ним. Последняя строка — код целиком: по нему вводят
 * вручную, если этикетка не сканируется (FR-M10-04b).
 *
 * @param qrPayload нагрузка QR с префиксом типа (§11.3)
 * @param title     крупная строка
 * @param lines     строки под заголовком
 * @param code      код для ручного ввода
 */
public record Label(String qrPayload, String title, List<String> lines, String code) {

    public Label {
        lines = List.copyOf(lines);
    }
}

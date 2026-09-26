package io.github.r0mbaa.wms.shared.marking;

import java.util.Locale;

/**
 * Полезная нагрузка QR-кода: {@code ПРЕФИКС:значение} (§11.3).
 *
 * <p>Префикс отличает код ячейки от кода товара, тары, задания и сотрудника. Благодаря этому
 * терминал распознаёт, что именно отсканировано, даже если порядок сканирования нарушен
 * (FR-M3-06). QR товара кодирует вид товара (SKU), а не экземпляр (FR-M3-06a).
 */
public sealed interface QrPayload {

    String encode();

    record Location(LocationCode code) implements QrPayload {
        public Location {
            if (code == null) {
                throw new IllegalArgumentException("Не указан код ячейки");
            }
        }

        @Override
        public String encode() {
            return "LOC:" + code.value();
        }
    }

    record Sku(String article) implements QrPayload {
        public Sku {
            requireToken("Артикул", article);
        }

        @Override
        public String encode() {
            return "SKU:" + article;
        }
    }

    record Container(String code) implements QrPayload {
        public Container {
            requireToken("Код тары", code);
        }

        @Override
        public String encode() {
            return "CNT:" + code;
        }
    }

    record Task(String id) implements QrPayload {
        public Task {
            requireToken("Номер задания", id);
        }

        @Override
        public String encode() {
            return "TSK:" + id;
        }
    }

    record Worker(String code) implements QrPayload {
        public Worker {
            requireToken("Код сотрудника", code);
        }

        @Override
        public String encode() {
            return "WRK:" + code;
        }
    }

    /**
     * @throws IllegalArgumentException с текстом, пригодным для показа сборщику (NFR-U-05)
     */
    static QrPayload parse(String raw) {
        String text = raw == null ? "" : raw.strip();
        int colon = text.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Код '" + text
                    + "' не является этикеткой системы: отсканируйте QR с префиксом LOC:, SKU:, CNT:, TSK: или WRK:");
        }
        String prefix = text.substring(0, colon).toUpperCase(Locale.ROOT);
        String value = text.substring(colon + 1);
        return switch (prefix) {
            case "LOC" -> new Location(LocationCode.parse(value));
            case "SKU" -> new Sku(value);
            case "CNT" -> new Container(value);
            case "TSK" -> new Task(value);
            case "WRK" -> new Worker(value);
            default -> throw new IllegalArgumentException("Неизвестный тип этикетки '" + prefix
                    + "': ожидается LOC, SKU, CNT, TSK или WRK");
        };
    }

    private static void requireToken(String what, String value) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(what + " не может быть пустым или содержать пробелы");
        }
    }
}

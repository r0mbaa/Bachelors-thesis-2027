package io.github.r0mbaa.wms.shared.marking;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Адрес ячейки хранения: {@code WH1-R03-12-4-2-K}, т.е. склад, ряд, секция, ярус, позиция
 * в ярусе и контрольный символ.
 *
 * <p>Адрес строится от номера ряда, а не прохода. Проход выводится из геометрии планировки
 * (§8.7, Б1) и может измениться при переносе ряда, а номер ряда присваивается при создании
 * и больше не меняется (§8.7, В2). Поэтому уже наклеенные этикетки остаются верными при
 * любом редактировании планировки, кроме явной перенумерации.
 *
 * <p>Нумерация секций и позиций ведётся от начала координат склада, а не от депо (§8.7, В1).
 * Ярусы нумеруются снизу, с 1.
 */
public record LocationCode(String warehouse, int row, int section, int level, int position) {

    public static final int MAX_ROW = 99;
    public static final int MAX_SECTION = 99;
    public static final int MAX_LEVEL = 9;
    public static final int MAX_POSITION = 9;

    private static final String WAREHOUSE_REGEX = "[A-Z][A-Z0-9]{0,5}";
    private static final Pattern WAREHOUSE = Pattern.compile(WAREHOUSE_REGEX);
    private static final Pattern FORMAT = Pattern.compile(
            "(" + WAREHOUSE_REGEX + ")-R(\\d{2})-(\\d{2})-(\\d)-(\\d)-([0-9A-Z])");

    public LocationCode {
        requireValidWarehouse(warehouse);
        requireRange("Номер ряда", row, MAX_ROW);
        requireRange("Номер секции", section, MAX_SECTION);
        requireRange("Номер яруса", level, MAX_LEVEL);
        requireRange("Номер позиции", position, MAX_POSITION);
    }

    /**
     * Разбирает полный код с контрольным символом. Регистр и пробелы по краям не важны.
     *
     * @throws IllegalArgumentException если формат нарушен или контрольный символ не совпадает
     */
    public static LocationCode parse(String text) {
        String normalized = text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
        Matcher m = FORMAT.matcher(normalized);
        if (!m.matches()) {
            throw new IllegalArgumentException("Код ячейки '" + text
                    + "' не соответствует формату WH1-R03-12-4-2-K: проверьте разделители и число цифр");
        }
        LocationCode code = new LocationCode(
                m.group(1),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)),
                Integer.parseInt(m.group(5)));
        if (code.checkCharacter() != m.group(6).charAt(0)) {
            throw new IllegalArgumentException("Код ячейки '" + text
                    + "' содержит опечатку (не сошёлся контрольный символ): сверьте код с этикеткой");
        }
        return code;
    }

    /** Адрес без контрольного символа: {@code WH1-R03-12-4-2}. */
    public String address() {
        return String.format(Locale.ROOT, "%s-R%02d-%02d-%d-%d", warehouse, row, section, level, position);
    }

    public char checkCharacter() {
        return CheckCharacter.compute(address().replace("-", ""));
    }

    /** Полный код с контрольным символом. Именно он печатается на этикетке и кодируется в QR. */
    public String value() {
        return address() + "-" + checkCharacter();
    }

    @Override
    public String toString() {
        return value();
    }

    public static String requireValidWarehouse(String warehouse) {
        if (warehouse == null || !WAREHOUSE.matcher(warehouse).matches()) {
            throw new IllegalArgumentException("Код склада '" + warehouse
                    + "' недопустим: нужна латинская буква и затем до 5 латинских букв или цифр, например WH1");
        }
        return warehouse;
    }

    private static void requireRange(String what, int value, int max) {
        if (value < 1 || value > max) {
            throw new IllegalArgumentException(what + " " + value + " вне диапазона 1.." + max);
        }
    }
}

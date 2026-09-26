package io.github.r0mbaa.wms.shared.marking;

/**
 * Контрольный символ по алгоритму Luhn mod N над алфавитом {@code [0-9A-Z]}, N = 36.
 *
 * <p>Обнаруживает любую одиночную замену символа и большинство перестановок соседних
 * символов, то есть типичные ошибки ручного ввода кода вместо сканирования (§11.3, FR-M10-04b).
 */
final class CheckCharacter {

    private static final int RADIX = 36;

    private CheckCharacter() {
    }

    /**
     * @param payload символы {@code [0-9A-Z]} без разделителей
     */
    static char compute(CharSequence payload) {
        int sum = 0;
        // Крайний правый символ полезной нагрузки удваивается: позицию с множителем 1
        // после неё займёт сам контрольный символ.
        boolean doubled = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            char c = payload.charAt(i);
            int value = Character.digit(c, RADIX);
            if (value < 0 || c > 'Z') {
                throw new IllegalArgumentException(
                        "Недопустимый символ '" + c + "': разрешены только цифры и латинские буквы");
            }
            int addend = doubled ? value * 2 : value;
            sum += addend / RADIX + addend % RADIX;
            doubled = !doubled;
        }
        int check = (RADIX - sum % RADIX) % RADIX;
        return Character.toUpperCase(Character.forDigit(check, RADIX));
    }
}

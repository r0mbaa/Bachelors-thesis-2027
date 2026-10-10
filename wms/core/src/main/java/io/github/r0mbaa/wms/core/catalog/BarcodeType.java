package io.github.r0mbaa.wms.core.catalog;

/** Тип штрихкода SKU (FR-M2-03). */
public enum BarcodeType {
    /** 13 цифр, последняя — контрольная: проверяется при вводе. */
    EAN13,
    CODE128,
    /** Внутренний код предприятия. */
    INTERNAL;

    /**
     * @throws IllegalArgumentException с указанием, что не так со штрихкодом
     */
    public String requireValid(String barcode) {
        if (barcode == null || barcode.isBlank() || barcode.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Штрихкод не может быть пустым или содержать пробелы");
        }
        if (this == EAN13) {
            if (!barcode.matches("\\d{13}")) {
                throw new IllegalArgumentException("Штрихкод EAN-13 '" + barcode + "' должен состоять из 13 цифр");
            }
            if (ean13CheckDigit(barcode) != barcode.charAt(12) - '0') {
                throw new IllegalArgumentException("Штрихкод EAN-13 '" + barcode
                        + "' с ошибкой: не сошлась контрольная цифра, сверьте код с упаковкой");
            }
        }
        return barcode;
    }

    /** Веса 1 и 3 попеременно слева направо по первым 12 цифрам. */
    static int ean13CheckDigit(String barcode) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = barcode.charAt(i) - '0';
            sum += i % 2 == 0 ? digit : digit * 3;
        }
        return (10 - sum % 10) % 10;
    }
}

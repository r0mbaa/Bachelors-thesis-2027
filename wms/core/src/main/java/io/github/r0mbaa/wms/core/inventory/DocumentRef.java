package io.github.r0mbaa.wms.core.inventory;

/**
 * Документ-основание движения (FR-M4-03): приход, задание, заказ, акт.
 *
 * @param type вид документа, константа вида {@code RECEIPT}
 * @param id   номер документа; у ручной операции может отсутствовать
 */
public record DocumentRef(String type, String id) {

    public static DocumentRef manual() {
        return new DocumentRef("MANUAL", null);
    }
}

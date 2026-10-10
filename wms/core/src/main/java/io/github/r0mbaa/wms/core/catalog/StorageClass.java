package io.github.r0mbaa.wms.core.catalog;

/** Класс хранения SKU (FR-M2-04): по нему проверяется совместимость товара с ячейкой. */
public enum StorageClass {
    NORMAL,
    FRAGILE,
    HEAVY,
    OVERSIZED
}

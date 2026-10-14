package io.github.r0mbaa.wms.core.topology;

/**
 * Ряды переведены в зону. Публикуется внутри транзакции: учёт остатков отменяет перевод, если
 * в зоне с выделенным местом один товар окажется в нескольких ячейках (INV-08).
 */
public record RowsAssignedToZone(long zoneId, String zoneCode, StoragePolicy policy) {
}

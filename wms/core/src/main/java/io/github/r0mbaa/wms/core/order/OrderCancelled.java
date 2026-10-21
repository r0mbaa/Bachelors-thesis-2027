package io.github.r0mbaa.wms.core.order;

/**
 * Заказ отменён. Публикуется в транзакции отмены: слушатели снимают резервы и убирают строки
 * из ещё не начатых заданий (FR-M5-07). Пакет заказов при этом не зависит от их пакетов.
 */
public record OrderCancelled(long orderId, String number) {
}

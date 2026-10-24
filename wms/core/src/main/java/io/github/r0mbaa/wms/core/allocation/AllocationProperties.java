package io.github.r0mbaa.wms.core.allocation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param reservationTtl срок жизни резерва заказа, ещё не вошедшего в задание (FR-M6-04)
 * @param maxAttempts    сколько раз повторяется аллокация заказа при конфликте версий
 *                       (NFR-R-02: конфликт вызывает повтор, а не ошибку у пользователя)
 */
@ConfigurationProperties("wms.allocation")
record AllocationProperties(
        @DefaultValue("4h") Duration reservationTtl,
        @DefaultValue("3") int maxAttempts) {
}

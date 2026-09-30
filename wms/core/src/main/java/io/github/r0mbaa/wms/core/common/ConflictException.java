package io.github.r0mbaa.wms.core.common;

import java.io.Serial;

/**
 * Операция корректна по форме, но противоречит текущему состоянию склада: не хватает остатка,
 * ячейка заблокирована, заказ уже отгружен. Отдаётся клиенту как 409.
 *
 * <p>Сообщение показывается пользователю как есть, поэтому в нём сказано, что сделать (NFR-U-05).
 */
public class ConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}

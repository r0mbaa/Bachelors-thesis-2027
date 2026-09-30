package io.github.r0mbaa.wms.core.common;

import java.io.Serial;

/** Запрошенной сущности нет. Отдаётся клиенту как 404. */
public class NotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}

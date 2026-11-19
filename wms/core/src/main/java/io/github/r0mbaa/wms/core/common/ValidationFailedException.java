package io.github.r0mbaa.wms.core.common;

import java.io.Serial;
import java.util.List;

/**
 * Документ целиком разобран, но не прошёл предметную проверку: например, в планировке
 * пересекаются ряды. Отдаётся клиенту как 422 со списком замечаний, по которым клиент
 * подсвечивает места ошибок.
 */
public class ValidationFailedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<?> issues;

    public ValidationFailedException(String message, List<?> issues) {
        super(message);
        this.issues = List.copyOf(issues);
    }

    public List<?> getIssues() {
        return issues;
    }
}

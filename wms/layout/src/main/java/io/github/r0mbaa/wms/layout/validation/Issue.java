package io.github.r0mbaa.wms.layout.validation;

import io.github.r0mbaa.wms.layout.model.Rect;
import java.util.List;

/**
 * Замечание к планировке с местом на плане, которое конструктор подсвечивает (FR-M15-07).
 *
 * @param message что не так и что сделать (NFR-U-05)
 * @param area    где подсветить; {@code null} — замечание ко всему плану
 * @param rows    номера затронутых рядов
 */
public record Issue(Severity severity, IssueType type, String message, Rect area, List<Integer> rows) {

    public Issue {
        rows = List.copyOf(rows);
    }

    public enum Severity {
        /** Планировку с ошибкой нельзя сохранить: по ней не построить маршруты. */
        ERROR,
        WARNING
    }
}

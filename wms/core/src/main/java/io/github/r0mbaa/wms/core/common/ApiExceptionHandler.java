package io.github.r0mbaa.wms.core.common;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Единый формат ошибок API: RFC 9457 (Problem Details). В {@code detail} лежит сообщение для
 * пользователя с указанием, что исправить (NFR-U-05), поэтому веб-панель и терминал
 * показывают его без переформулировки.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Не найдено", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Операция невозможна", e.getMessage());
    }

    /** Так сообщают об ошибке ввода value-типы: {@code LocationCode}, {@code QrPayload}, модель планировки. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректные данные", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Данные изменились",
                "Запись одновременно изменил другой пользователь: обновите данные и повторите операцию");
    }

    /**
     * Последний рубеж: сработало ограничение БД там, где сервис не проверил правило сам. Текст
     * ошибки СУБД содержит имя ограничения, по нему место находится в схеме.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityViolation(DataIntegrityViolationException e) {
        ProblemDetail body = problem(HttpStatus.CONFLICT, "Нарушена целостность данных",
                "Операция противоречит ограничению базы данных: проверьте введённые значения");
        body.setProperty("cause", e.getMostSpecificCause().getMessage());
        return body;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        String fields = String.join(", ", errors.stream().map(error -> error.get("field")).toList());
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Некорректные данные", "Исправьте поля запроса: " + fields);
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return body;
    }
}

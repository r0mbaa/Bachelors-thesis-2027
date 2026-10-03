package io.github.r0mbaa.wms.core.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Страница списка в ответе API. Свой тип, а не {@code Page} из Spring Data: JSON-представление
 * {@code PageImpl} не является стабильным контрактом.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

    public static final int MAX_SIZE = 500;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements());
    }

    /** Размер страницы из запроса, ограниченный сверху: список на 100 тыс. ячеек не отдаётся одним ответом. */
    public static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_SIZE);
    }
}

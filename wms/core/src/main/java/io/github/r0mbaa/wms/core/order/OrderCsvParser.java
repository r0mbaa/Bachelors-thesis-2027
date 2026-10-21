package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.order.CustomerOrder.OrderHeader;
import io.github.r0mbaa.wms.core.order.OrderService.LineSpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Разбор выгрузки заказов в CSV (FR-M5-02): одна строка файла — одна строка заказа, строки с
 * одинаковым номером собираются в один заказ. Разделитель — {@code ;} (так сохраняет Excel с
 * русской локалью) или {@code ,}, определяется по заголовку.
 *
 * <pre>
 * number;counterparty;priority;deadline;carrier;direction;sku;quantity
 * SO-1;ООО Ромашка;7;2026-10-20T18:00:00+03:00;СДЭК;Север;85123A;2
 * </pre>
 *
 * Приоритет, дедлайн, перевозчик и направление можно оставить пустыми.
 */
final class OrderCsvParser {

    static final List<String> COLUMNS =
            List.of("number", "counterparty", "priority", "deadline", "carrier", "direction", "sku", "quantity");

    private OrderCsvParser() {
    }

    /**
     * @throws IllegalArgumentException с номером строки файла, если строка не разбирается
     */
    static List<ParsedOrder> parse(String csv) {
        List<String> rows = csv == null ? List.of() : csv.strip().lines().filter(l -> !l.isBlank()).toList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Файл пуст: нужна строка заголовка " + String.join(";", COLUMNS)
                    + " и хотя бы одна строка заказа");
        }
        String header = rows.getFirst().replace("\uFEFF", "");
        String separator = header.contains(";") ? ";" : ",";
        List<String> columns = Arrays.stream(header.split(separator, -1))
                .map(c -> c.strip().toLowerCase(Locale.ROOT)).toList();
        if (!columns.equals(COLUMNS)) {
            throw new IllegalArgumentException("Заголовок файла должен быть: " + String.join(separator, COLUMNS));
        }

        Map<String, ParsedOrder> orders = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            int fileLine = i + 1;
            String[] cells = rows.get(i).split(separator, -1);
            if (cells.length != COLUMNS.size()) {
                throw new IllegalArgumentException("Строка " + fileLine + ": ожидается " + COLUMNS.size()
                        + " полей через «" + separator + "», найдено " + cells.length);
            }
            String number = required(cells[0], "номер заказа", fileLine);
            ParsedOrder order = orders.get(number);
            if (order == null) {
                order = new ParsedOrder(new OrderHeader(number, required(cells[1], "контрагент", fileLine),
                        optionalInt(cells[2], "приоритет", fileLine), optionalInstant(cells[3], fileLine),
                        cells[4].strip(), cells[5].strip(), false), new ArrayList<>());
                orders.put(number, order);
            }
            Integer quantity = optionalInt(required(cells[7], "количество", fileLine), "количество", fileLine);
            order.lines().add(new LineSpec(required(cells[6], "товар", fileLine), quantity));
        }
        return List.copyOf(orders.values());
    }

    private static String required(String value, String what, int line) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Строка " + line + ": не заполнено поле «" + what + "»");
        }
        return value.strip();
    }

    private static Integer optionalInt(String value, String what, int line) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Строка " + line + ": поле «" + what + "» должно быть целым числом, задано '"
                    + value.strip() + "'");
        }
    }

    private static Instant optionalInstant(String value, int line) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.strip()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Строка " + line + ": дедлайн '" + value.strip()
                    + "' не разобран, нужен формат 2026-10-20T18:00:00+03:00");
        }
    }

    record ParsedOrder(OrderHeader header, List<LineSpec> lines) {
    }
}

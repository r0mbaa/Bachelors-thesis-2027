package io.github.r0mbaa.wms.core.print;

import io.github.r0mbaa.wms.core.task.TaskViews.StepView;
import io.github.r0mbaa.wms.core.task.TaskViews.TaskSummary;
import io.github.r0mbaa.wms.core.task.TaskViews.TaskView;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Маршрутный лист задания (FR-M11-03, §15.4): шаги в порядке обхода с ячейкой, ярусом, товаром,
 * количеством и отделением тележки. Нужен, когда терминала нет под рукой, и как бумажный след
 * задания. QR {@code TSK:…} открывает задание на терминале.
 */
final class RouteSheet {

    private static final float MARGIN = 15;
    private static final float ROW = 7;
    private static final float TABLE_TOP_FIRST = 64;
    private static final float TABLE_TOP_NEXT = 20;
    private static final float FOOTER = PdfCanvas.PAGE_HEIGHT - 10;
    private static final float QR_SIDE = 28;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** Ширины столбцов, мм: всего 180 — ширина листа без полей. */
    private static final float[] WIDTHS = {10, 44, 12, 26, 50, 14, 14, 10};
    private static final String[] HEADERS = {"№", "Ячейка", "Ярус", "Артикул", "Наименование", "Кол.", "Отд.", "✓"};

    private RouteSheet() {
    }

    static byte[] render(TaskView task, ZoneId zone) {
        List<List<StepView>> pages = paginate(task.steps());
        try (PdfCanvas canvas = new PdfCanvas()) {
            for (int p = 0; p < pages.size(); p++) {
                canvas.newPage();
                float top = TABLE_TOP_NEXT;
                if (p == 0) {
                    header(canvas, task, zone);
                    top = TABLE_TOP_FIRST;
                } else {
                    canvas.text(canvas.bold, 10, MARGIN, 14, "Маршрутный лист " + task.summary().number()
                            + " (продолжение)");
                }
                table(canvas, pages.get(p), top);
                String footer = "Стр. " + (p + 1) + " из " + pages.size();
                canvas.text(canvas.regular, 8, PdfCanvas.PAGE_WIDTH - MARGIN - canvas.width(canvas.regular, 8, footer),
                        FOOTER, footer);
            }
            return canvas.toBytes();
        }
    }

    private static void header(PdfCanvas canvas, TaskView task, ZoneId zone) {
        TaskSummary summary = task.summary();
        canvas.qr(new QrPayload.Task(summary.number()).encode(), PdfCanvas.PAGE_WIDTH - MARGIN - QR_SIDE, 12, QR_SIDE);
        canvas.text(canvas.bold, 16, MARGIN, 22, "Маршрутный лист");
        canvas.text(canvas.bold, 12, MARGIN, 30, "Задание " + summary.number());
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < summary.orders().size(); i++) {
            slots.add("отд. " + (i + 1) + " — " + summary.orders().get(i));
        }
        float width = PdfCanvas.PAGE_WIDTH - 2 * MARGIN - QR_SIDE - 5;
        String[] lines = {
            "Тара: " + summary.container(),
            "Заказы: " + String.join("; ", slots),
            "Сборщик: " + (summary.worker() == null ? "не назначен" : summary.worker()),
            "Сформировано: " + TIME.withZone(zone).format(summary.createdAt()) + " · маршрут: "
                    + summary.algorithmRouting() + " · шагов: " + summary.steps(),
        };
        float baseline = 38;
        for (String line : lines) {
            canvas.text(canvas.regular, 9, MARGIN, baseline, canvas.truncate(canvas.regular, 9, width, line));
            baseline += 5.5f;
        }
    }

    private static void table(PdfCanvas canvas, List<StepView> steps, float top) {
        cells(canvas, HEADERS, top, true);
        float y = top + ROW;
        canvas.line(MARGIN, y, PdfCanvas.PAGE_WIDTH - MARGIN, y, 0.8f);
        for (StepView step : steps) {
            cells(canvas, new String[] {
                String.valueOf(step.sequence()), step.location(), step.level() == null ? "" : String.valueOf(step.level()),
                step.article(), step.skuName(), String.valueOf(step.required()), String.valueOf(step.slot()), ""},
                    y, false);
            float x = MARGIN;
            for (int i = 0; i < WIDTHS.length - 1; i++) {
                x += WIDTHS[i];
            }
            canvas.box(x + 2.5f, y + 1.5f, 4, 4, 0.5f);
            y += ROW;
            canvas.line(MARGIN, y, PdfCanvas.PAGE_WIDTH - MARGIN, y, 0.2f);
        }
    }

    private static void cells(PdfCanvas canvas, String[] values, float top, boolean bold) {
        float x = MARGIN;
        for (int i = 0; i < values.length; i++) {
            if (!values[i].isEmpty()) {
                PDFont font = bold ? canvas.bold : canvas.regular;
                float size = i == 1 ? canvas.fitSize(font, 9, 6.5f, WIDTHS[i] - 2, values[i]) : 9;
                canvas.text(font, size, x + 1, top + ROW - 2, canvas.truncate(font, size, WIDTHS[i] - 2, values[i]));
            }
            x += WIDTHS[i];
        }
    }

    private static List<List<StepView>> paginate(List<StepView> steps) {
        int first = (int) ((FOOTER - 6 - TABLE_TOP_FIRST - ROW) / ROW);
        int next = (int) ((FOOTER - 6 - TABLE_TOP_NEXT - ROW) / ROW);
        List<List<StepView>> pages = new ArrayList<>();
        int from = 0;
        do {
            int size = pages.isEmpty() ? first : next;
            pages.add(steps.subList(from, Math.min(steps.size(), from + size)));
            from += size;
        } while (from < steps.size());
        return pages;
    }
}

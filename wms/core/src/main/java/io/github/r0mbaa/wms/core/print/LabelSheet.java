package io.github.r0mbaa.wms.core.print;

import java.util.List;

/**
 * Лист самоклеящихся этикеток A4: 3 × 8 этикеток 70 × 37 мм, самый распространённый формат.
 * Печать на обычном офисном принтере, без специального принтера этикеток.
 */
final class LabelSheet {

    static final int COLUMNS = 3;
    static final int ROWS = 8;
    static final float WIDTH = 70;
    static final float HEIGHT = 37;
    /** Лист 297 мм, этикетки занимают 296: полмиллиметра сверху и снизу. */
    static final float TOP = 0.5f;

    private static final float PADDING = 4;
    private static final float QR_SIDE = HEIGHT - 2 * PADDING;
    private static final float GAP = 3;

    private LabelSheet() {
    }

    static byte[] render(List<Label> labels) {
        try (PdfCanvas canvas = new PdfCanvas()) {
            for (int i = 0; i < labels.size(); i++) {
                if (i % (COLUMNS * ROWS) == 0) {
                    canvas.newPage();
                }
                float[] origin = origin(i);
                draw(canvas, labels.get(i), origin[0], origin[1]);
            }
            if (labels.isEmpty()) {
                canvas.newPage();
            }
            return canvas.toBytes();
        }
    }

    /** Левый верхний угол этикетки с номером {@code index} на её листе, мм. */
    static float[] origin(int index) {
        int slot = index % (COLUMNS * ROWS);
        return new float[] {(slot % COLUMNS) * WIDTH, TOP + (slot / COLUMNS) * HEIGHT};
    }

    private static void draw(PdfCanvas canvas, Label label, float x, float y) {
        canvas.qr(label.qrPayload(), x + PADDING, y + PADDING, QR_SIDE);
        float textX = x + PADDING + QR_SIDE + GAP;
        float textWidth = WIDTH - (textX - x) - PADDING;

        float titleSize = canvas.fitSize(canvas.bold, 11, 6, textWidth, label.title());
        float baseline = y + PADDING + 4;
        canvas.text(canvas.bold, titleSize, textX, baseline, canvas.truncate(canvas.bold, titleSize, textWidth,
                label.title()));
        for (String line : label.lines()) {
            baseline += 5;
            canvas.text(canvas.regular, 8, textX, baseline, canvas.truncate(canvas.regular, 8, textWidth, line));
        }
        float codeSize = canvas.fitSize(canvas.regular, 7, 4.5f, textWidth, label.code());
        canvas.text(canvas.regular, codeSize, textX, y + HEIGHT - PADDING, label.code());
    }
}

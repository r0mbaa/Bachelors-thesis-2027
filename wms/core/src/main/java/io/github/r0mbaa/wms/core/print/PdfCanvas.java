package io.github.r0mbaa.wms.core.print;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Страницы A4 с текстом и QR-кодами. Координаты в миллиметрах от левого верхнего угла, как на
 * макете этикетки, а не в пунктах от нижнего левого, как в PDF.
 *
 * <p>Шрифт DejaVu Sans встраивается подмножеством: без него кириллица в PDF не отображается.
 * QR рисуется векторными прямоугольниками, а не картинкой, поэтому печатается чётко на любом
 * принтере.
 */
final class PdfCanvas implements AutoCloseable {

    static final float PAGE_WIDTH = 210;
    static final float PAGE_HEIGHT = 297;

    private static final float POINTS_PER_MM = 72f / 25.4f;

    /** Уровень Q восстанавливает до 25 % модулей: этикетка на складе мнётся и пачкается. */
    private static final Map<EncodeHintType, Object> QR_HINTS = Map.of(
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q,
            EncodeHintType.MARGIN, 0,
            EncodeHintType.CHARACTER_SET, "UTF-8");

    private final PDDocument document = new PDDocument();
    final PDFont regular;
    final PDFont bold;
    private PDPageContentStream stream;

    PdfCanvas() {
        this.regular = font("fonts/DejaVuSans.ttf");
        this.bold = font("fonts/DejaVuSans-Bold.ttf");
    }

    void newPage() {
        try {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @param baseline расстояние от верха страницы до базовой линии текста, мм */
    void text(PDFont font, float size, float x, float baseline, String text) {
        try {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(pt(x), pt(PAGE_HEIGHT - baseline));
            stream.showText(text);
            stream.endText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ширина текста, мм. */
    float width(PDFont font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000 * size / POINTS_PER_MM;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Наибольший кегль не больше {@code size}, при котором текст влезает в {@code maxWidth}. */
    float fitSize(PDFont font, float size, float minSize, float maxWidth, String text) {
        float fitted = size;
        while (fitted > minSize && width(font, fitted, text) > maxWidth) {
            fitted -= 0.5f;
        }
        return fitted;
    }

    /** Текст, обрезанный многоточием до ширины {@code maxWidth}. */
    String truncate(PDFont font, float size, float maxWidth, String text) {
        if (width(font, size, text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && width(font, size, cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "…";
    }

    /**
     * QR-код квадратом со стороной {@code side} мм. Соседние тёмные модули строки сливаются в один
     * прямоугольник: страница этикеток весит в разы меньше.
     */
    void qr(String payload, float x, float top, float side) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, QR_HINTS);
        } catch (WriterException e) {
            throw new IllegalArgumentException("QR для '" + payload + "' не построен: " + e.getMessage(), e);
        }
        float module = side / matrix.getWidth();
        try {
            for (int row = 0; row < matrix.getHeight(); row++) {
                int col = 0;
                while (col < matrix.getWidth()) {
                    if (!matrix.get(col, row)) {
                        col++;
                        continue;
                    }
                    int start = col;
                    while (col < matrix.getWidth() && matrix.get(col, row)) {
                        col++;
                    }
                    stream.addRect(pt(x + start * module), pt(PAGE_HEIGHT - top - (row + 1) * module),
                            pt((col - start) * module), pt(module));
                }
            }
            stream.fill();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void line(float x1, float y1, float x2, float y2, float thickness) {
        try {
            stream.setLineWidth(thickness);
            stream.moveTo(pt(x1), pt(PAGE_HEIGHT - y1));
            stream.lineTo(pt(x2), pt(PAGE_HEIGHT - y2));
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void box(float x, float top, float width, float height, float thickness) {
        try {
            stream.setLineWidth(thickness);
            stream.addRect(pt(x), pt(PAGE_HEIGHT - top - height), pt(width), pt(height));
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    byte[] toBytes() {
        try {
            closeStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            closeStream();
            document.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void closeStream() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    private PDFont font(String resource) {
        try (InputStream in = PdfCanvas.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Шрифт " + resource + " не найден в classpath: нужна зависимость "
                        + "net.sourceforge.jeuclid:dejavu-fonts");
            }
            return PDType0Font.load(document, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static float pt(float mm) {
        return mm * POINTS_PER_MM;
    }
}

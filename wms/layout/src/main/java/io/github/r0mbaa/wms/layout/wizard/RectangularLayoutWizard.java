package io.github.r0mbaa.wms.layout.wizard;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Параметрический генератор типовой прямоугольной планировки (FR-M15-09). Один и тот же код
 * служит мастеру конструктора и генератору топологий стенда (FR-M14-01), поэтому эксперимент
 * идёт на тех же складах, что строит пользователь.
 *
 * <p>Поперёк склада повторяется шаг {@code d = w_a + 2·w_r}: ряд лицом на восток, проход, ряд
 * лицом на запад. Соседние проходы разделены парой рядов, стоящих спиной к спине. Крайние ряды
 * стоят у стен. Вдоль склада блоки рядов чередуются с поперечными проходами; передний и
 * задний поперечные проходы есть всегда.
 *
 * <p>Ряды нумеруются блок за блоком от переднего, внутри блока слева направо, т.е. от начала
 * координат склада (§8.7, В1): в блоке {@code b} ряды {@code 2·a·b + 1 .. 2·a·(b + 1)}.
 */
public final class RectangularLayoutWizard {

    private RectangularLayoutWizard() {
    }

    /** @return планировка с версией 0: её ещё предстоит сохранить */
    public static Layout generate(RectangularLayoutParameters p) {
        return generate(p, 0);
    }

    /**
     * @param baseVersion версия, поверх которой планировка будет сохранена
     */
    public static Layout generate(RectangularLayoutParameters p, long baseVersion) {
        double depth = p.profile().depth();
        List<String> sections = Collections.nCopies(p.sectionsPerBlock(), p.profile().name());
        List<RackRow> rows = new ArrayList<>();
        int number = 1;
        for (int b = 0; b < p.blocks(); b++) {
            double y = p.crossAisleWidth() * (b + 1) + b * p.blockLength();
            for (int a = 0; a < p.aisles(); a++) {
                double x = a * p.aislePitch();
                rows.add(new RackRow(number++, new Point(x, y), Facing.EAST, sections));
                rows.add(new RackRow(number++, new Point(x + depth + p.aisleWidth(), y), Facing.WEST, sections));
            }
        }
        return new Layout(p.warehouseCode(), baseVersion, p.width(), p.length(), depot(p), List.of(p.profile()), rows);
    }

    /** Депо на оси переднего поперечного прохода. */
    private static Point depot(RectangularLayoutParameters p) {
        double firstAisleAxis = p.profile().depth() + p.aisleWidth() / 2;
        double x = switch (p.depotPosition()) {
            case FRONT_LEFT -> firstAisleAxis;
            case FRONT_CENTER -> p.width() / 2;
            case FRONT_RIGHT -> firstAisleAxis + (p.aisles() - 1) * p.aislePitch();
        };
        return new Point(x, p.crossAisleWidth() / 2);
    }
}

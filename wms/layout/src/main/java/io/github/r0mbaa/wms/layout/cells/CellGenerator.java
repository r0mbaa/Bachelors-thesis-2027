package io.github.r0mbaa.wms.layout.cells;

import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackProfile;
import io.github.r0mbaa.wms.layout.model.RackRow;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.ArrayList;
import java.util.List;

/**
 * Автогенерация ячеек с кодами по шаблону (FR-M15-05) и метрическими координатами (FR-M1-04).
 *
 * <p>Порядок результата детерминирован: ряды в порядке планировки, внутри ряда секции,
 * ярусы снизу вверх и позиции вдоль ряда.
 */
public final class CellGenerator {

    private CellGenerator() {
    }

    public static List<Cell> generate(Layout layout) {
        List<Cell> cells = new ArrayList<>();
        for (RackRow row : layout.rows()) {
            double rowDepth = layout.rowDepth(row);
            // Расстояние вдоль ряда от origin до начала текущей секции.
            double sectionStart = 0;
            for (int s = 0; s < row.sections().size(); s++) {
                RackProfile profile = layout.profile(row.sections().get(s));
                for (int level = 1; level <= profile.levels(); level++) {
                    for (int position = 1; position <= profile.cellsPerLevel(); position++) {
                        double along = sectionStart + (position - 0.5) * profile.cellWidth();
                        cells.add(new Cell(
                                new LocationCode(layout.warehouseCode(), row.number(), s + 1, level, position),
                                place(row, rowDepth, along, profile.depth() / 2),
                                profile.shelfHeight(level),
                                place(row, rowDepth, along, 0),
                                row.facing()));
                    }
                }
                sectionStart += profile.sectionWidth();
            }
        }
        return cells;
    }

    /**
     * Точка ряда в координатах склада.
     *
     * @param along    расстояние вдоль ряда от origin
     * @param fromFace расстояние от лицевой линии вглубь стеллажа; 0 — сама лицевая линия
     */
    private static Point place(RackRow row, double rowDepth, double along, double fromFace) {
        double x0 = row.origin().x();
        double y0 = row.origin().y();
        return switch (row.facing()) {
            case NORTH -> new Point(x0 + along, y0 + rowDepth - fromFace);
            case SOUTH -> new Point(x0 + along, y0 + fromFace);
            case EAST -> new Point(x0 + rowDepth - fromFace, y0 + along);
            case WEST -> new Point(x0 + fromFace, y0 + along);
        };
    }
}

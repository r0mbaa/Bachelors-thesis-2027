package io.github.r0mbaa.wms.core.support;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Layout;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.layout.model.RackKind;
import io.github.r0mbaa.wms.layout.model.RackProfile;
import io.github.r0mbaa.wms.layout.model.RackRow;
import java.util.List;

/**
 * Маленькие планировки для тестов. Полочный профиль «S»: 3 яруса (0,5 / 0,4 / 0,4 м), 2 ячейки
 * по 0,6 м в ярусе, глубина 0,5 м, 150 кг на ярус; секция даёт 6 ячеек.
 */
public final class TestLayouts {

    public static final RackProfile SHELF =
            new RackProfile("S", RackKind.SHELF, List.of(0.5, 0.4, 0.4), 2, 0.6, 0.5, 150);

    private TestLayouts() {
    }

    /** Два ряда по две секции лицом друг к другу через проход 2,5 м: 24 ячейки. */
    public static Layout twoRows(String warehouse, long baseVersion) {
        return layout(warehouse, baseVersion,
                new RackRow(1, new Point(2, 5), Facing.NORTH, List.of("S", "S")),
                new RackRow(2, new Point(2, 8), Facing.SOUTH, List.of("S", "S")));
    }

    public static Layout layout(String warehouse, long baseVersion, RackRow... rows) {
        return new Layout(warehouse, baseVersion, 20, 15, new Point(0, 0), List.of(SHELF), List.of(rows));
    }
}

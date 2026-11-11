package io.github.r0mbaa.wms.layout.model;

import java.util.List;

/**
 * Типовые профили стеллажей (FR-M15-03d), с которыми поставляется система. Размеры взяты из
 * распространённых каталожных исполнений и служат отправной точкой: пользователь создаёт свои
 * профили на их основе.
 */
public final class StandardProfiles {

    /** 5 ярусов по 0,4 м, 3 ячейки по 0,4 м: секция 1,2 м под мелкоштучный товар. */
    public static final RackProfile SHELF_5 = new RackProfile("Полочный 5-ярусный", RackKind.SHELF,
            List.of(0.45, 0.4, 0.4, 0.4, 0.4), 3, 0.4, 0.6, 150);

    /** Нижний ярус выше остальных — под крупный товар (FR-M15-03b). */
    public static final RackProfile SHELF_3 = new RackProfile("Полочный 3-ярусный", RackKind.SHELF,
            List.of(0.6, 0.5, 0.5), 3, 0.4, 0.6, 200);

    /** Три европаллеты в ярусе: секция 2,7 м, как в примере §8.7 А2. */
    public static final RackProfile PALLET_3 = new RackProfile("Паллетный 3-ярусный", RackKind.PALLET,
            List.of(1.6, 1.5, 1.5), 3, 0.9, 1.1, 3000);

    /** Два этажа полок: верхний этаж на высоте 2,6 м, к нему поднимаются по лестнице. */
    public static final RackProfile MEZZANINE = new RackProfile("Мезонинный", RackKind.MEZZANINE,
            List.of(2.6, 2.6), 3, 0.4, 0.6, 250);

    /** Место на полу под паллету или крупногабаритный товар. */
    public static final RackProfile FLOOR = new RackProfile("Напольная зона", RackKind.FLOOR,
            List.of(1.8), 1, 1.2, 1.2, 1500);

    public static final List<RackProfile> ALL = List.of(SHELF_5, SHELF_3, PALLET_3, MEZZANINE, FLOOR);

    private StandardProfiles() {
    }

    public static RackProfile byName(String name) {
        return ALL.stream()
                .filter(p -> p.name().equalsIgnoreCase(name == null ? "" : name.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Типового профиля '" + name + "' нет: доступны "
                        + ALL.stream().map(RackProfile::name).toList()));
    }
}

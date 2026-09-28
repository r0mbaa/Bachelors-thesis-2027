package io.github.r0mbaa.wms.layout.model;

import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.List;
import java.util.Objects;

/**
 * Ряд стеллажей на плане (FR-M15-02): цепочка секций, в том числе разных профилей
 * (FR-M15-03a). Секции разной глубины выравниваются по лицевой линии ряда.
 *
 * @param number   номер ряда; присваивается при создании и не меняется (§8.7, В2), входит в адрес ячейки
 * @param origin   угол габарита ряда с наименьшими X и Y
 * @param facing   сторона, обращённая к проходу
 * @param sections имена профилей секций по возрастанию координаты вдоль ряда, т.е. от начала
 *                 координат склада, а не от депо (§8.7, В1)
 */
public record RackRow(int number, Point origin, Facing facing, List<String> sections) {

    public RackRow {
        if (number < 1 || number > LocationCode.MAX_ROW) {
            throw new IllegalArgumentException("Номер ряда должен быть от 1 до " + LocationCode.MAX_ROW + ", задано " + number);
        }
        Objects.requireNonNull(origin, "Не задано положение ряда " + number);
        Objects.requireNonNull(facing, "Не задана ориентация ряда " + number);
        sections = List.copyOf(sections);
        if (sections.isEmpty() || sections.size() > LocationCode.MAX_SECTION) {
            throw new IllegalArgumentException("Ряд " + number + ": число секций должно быть от 1 до "
                    + LocationCode.MAX_SECTION + ", задано " + sections.size());
        }
    }
}

package io.github.r0mbaa.wms.layout.cells;

import io.github.r0mbaa.wms.layout.model.Facing;
import io.github.r0mbaa.wms.layout.model.Point;
import io.github.r0mbaa.wms.shared.marking.LocationCode;

/**
 * Ячейка хранения, выведенная из планировки.
 *
 * @param center      центр ячейки на плане, м
 * @param shelfHeight высота полки над полом, м: координата z (FR-M1-04), аргумент штрафа h(z) (§8.1)
 * @param access      точка на лицевой линии ряда напротив центра ячейки. От неё граф проецирует
 *                    ячейку на ось прохода (§8.7, А1–А3)
 * @param facing      куда обращена лицевая сторона, т.е. с какой стороны к ячейке подходят
 */
public record Cell(LocationCode code, Point center, double shelfHeight, Point access, Facing facing) {
}

package io.github.r0mbaa.wms.layout.wizard;

import io.github.r0mbaa.wms.layout.model.RackProfile;
import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.Objects;

/**
 * Параметры типовой прямоугольной планировки (FR-M15-09, FR-M14-01): параллельные проходы вдоль
 * оси Y, поперечные проходы вдоль оси X, по два ряда стеллажей на проход.
 *
 * @param aisles           число проходов с отбором
 * @param crossAisles      число поперечных проходов, включая передний и задний; не меньше 2.
 *                         Блоков по длине на один меньше
 * @param sectionsPerBlock секций в ряду одного блока: длина прохода в блоке равна
 *                         {@code sectionsPerBlock × ширина секции}
 * @param profile          профиль всех стеллажей
 * @param aisleWidth       ширина прохода между лицевыми сторонами рядов, м ({@code w_a})
 * @param crossAisleWidth  ширина поперечного прохода, м
 */
public record RectangularLayoutParameters(
        String warehouseCode,
        int aisles,
        int crossAisles,
        int sectionsPerBlock,
        RackProfile profile,
        double aisleWidth,
        double crossAisleWidth,
        DepotPosition depotPosition) {

    public RectangularLayoutParameters {
        LocationCode.requireValidWarehouse(warehouseCode);
        Objects.requireNonNull(profile, "Не задан профиль стеллажей");
        Objects.requireNonNull(depotPosition, "Не задано положение депо");
        if (aisles < 1) {
            throw new IllegalArgumentException("Нужен хотя бы один проход, задано " + aisles);
        }
        if (crossAisles < 2) {
            throw new IllegalArgumentException("Поперечных проходов не меньше двух (передний и задний), задано "
                    + crossAisles);
        }
        if (sectionsPerBlock < 1 || sectionsPerBlock > LocationCode.MAX_SECTION) {
            throw new IllegalArgumentException("Секций в ряду блока должно быть от 1 до " + LocationCode.MAX_SECTION
                    + ", задано " + sectionsPerBlock);
        }
        int rows = 2 * aisles * (crossAisles - 1);
        if (rows > LocationCode.MAX_ROW) {
            throw new IllegalArgumentException("Получится " + rows + " рядов, а адрес ячейки вмещает не больше "
                    + LocationCode.MAX_ROW + ": уменьшите число проходов или поперечных проходов");
        }
        requirePositive("Ширина прохода", aisleWidth);
        requirePositive("Ширина поперечного прохода", crossAisleWidth);
    }

    public int blocks() {
        return crossAisles - 1;
    }

    /** Длина прохода в одном блоке ({@code L}, §8.1). */
    public double blockLength() {
        return sectionsPerBlock * profile.sectionWidth();
    }

    /** Шаг между осями соседних проходов: {@code d = w_a + 2·w_r} (§8.1). */
    public double aislePitch() {
        return aisleWidth + 2 * profile.depth();
    }

    /**
     * Коэффициент формы {@code k = L / d} (§8.1): во сколько раз пройти проход насквозь дороже,
     * чем перейти в соседний. Именно его, а не размеры по отдельности, варьирует эксперимент.
     */
    public double shapeFactor() {
        return blockLength() / aislePitch();
    }

    public double width() {
        return aisles * aislePitch();
    }

    public double length() {
        return crossAisles * crossAisleWidth + blocks() * blockLength();
    }

    private static void requirePositive(String what, double value) {
        if (!(value > 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(what + ": нужно положительное число, задано " + value);
        }
    }
}

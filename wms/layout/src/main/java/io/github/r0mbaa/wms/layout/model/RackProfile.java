package io.github.r0mbaa.wms.layout.model;

import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.List;
import java.util.Objects;

/**
 * Профиль стеллажа (FR-M15-03): переиспользуемое описание секции, на которое ссылаются
 * секции рядов на плане. Ярусы одного профиля могут иметь разную высоту (FR-M15-03b).
 *
 * @param levelHeights высоты ярусов снизу вверх, м; число элементов равно числу ярусов
 * @param cellWidth    ширина ячейки вдоль ряда, м
 * @param depth        глубина стеллажа поперёк ряда, м
 */
public record RackProfile(
        String name,
        RackKind kind,
        List<Double> levelHeights,
        int cellsPerLevel,
        double cellWidth,
        double depth,
        double maxLoadPerLevelKg) {

    public RackProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Не задано наименование профиля стеллажа");
        }
        Objects.requireNonNull(kind, "Не задан тип стеллажа профиля '" + name + "'");
        levelHeights = List.copyOf(levelHeights);
        if (levelHeights.isEmpty() || levelHeights.size() > LocationCode.MAX_LEVEL) {
            throw new IllegalArgumentException("Профиль '" + name + "': число ярусов должно быть от 1 до "
                    + LocationCode.MAX_LEVEL + ", задано " + levelHeights.size());
        }
        for (double height : levelHeights) {
            requirePositive(name, "высота яруса", height);
        }
        if (cellsPerLevel < 1 || cellsPerLevel > LocationCode.MAX_POSITION) {
            throw new IllegalArgumentException("Профиль '" + name + "': число ячеек в ярусе должно быть от 1 до "
                    + LocationCode.MAX_POSITION + ", задано " + cellsPerLevel);
        }
        requirePositive(name, "ширина ячейки", cellWidth);
        requirePositive(name, "глубина", depth);
        requirePositive(name, "максимальная нагрузка на ярус", maxLoadPerLevelKg);
    }

    public int levels() {
        return levelHeights.size();
    }

    public double sectionWidth() {
        return cellsPerLevel * cellWidth;
    }

    public double totalHeight() {
        return shelfHeight(levels()) + levelHeights.getLast();
    }

    /**
     * Высота полки яруса над полом, м: сумма высот нижележащих ярусов. Именно она, а не номер
     * яруса, служит аргументом вертикального штрафа h(z): номера ярусов разных профилей
     * физически несопоставимы (§8.1).
     *
     * @param level номер яруса, начиная с 1
     */
    public double shelfHeight(int level) {
        if (level < 1 || level > levels()) {
            throw new IllegalArgumentException("Профиль '" + name + "': нет яруса " + level);
        }
        double height = 0;
        for (int i = 0; i < level - 1; i++) {
            height += levelHeights.get(i);
        }
        return height;
    }

    private static void requirePositive(String profile, String what, double value) {
        if (!(value > 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Профиль '" + profile + "': " + what
                    + " должна быть положительным числом, задано " + value);
        }
    }
}

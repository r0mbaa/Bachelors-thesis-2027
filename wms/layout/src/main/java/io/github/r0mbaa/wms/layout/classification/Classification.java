package io.github.r0mbaa.wms.layout.classification;

import java.util.List;

/**
 * Результат классификации планировки (FR-M15-08).
 *
 * @param aisles        число проходов с отбором; 0, если структуру проходов выделить не удалось
 * @param blocks        число блоков рядов вдоль проходов
 * @param aislePitch    средний шаг между осями проходов {@code d}, м
 * @param aisleLength   средняя длина прохода в блоке {@code L}, м
 * @param shapeFactor   коэффициент формы {@code k = L / d} (§8.1); {@code NaN}, если не определён
 * @param deadEndAisles у проходов нет выхода с одного из концов (§8.7, Б4): стратегии со
 *                      сквозным проходом (S-shape) неприменимы
 * @param deviations    почему планировка не попала в регулярный класс; пусто для регулярной
 */
public record Classification(
        LayoutClass layoutClass,
        int aisles,
        int blocks,
        double aislePitch,
        double aisleLength,
        double shapeFactor,
        boolean deadEndAisles,
        List<String> deviations) {

    public Classification {
        deviations = List.copyOf(deviations);
    }

    public boolean isRegular() {
        return layoutClass != LayoutClass.GENERAL_GRAPH;
    }
}

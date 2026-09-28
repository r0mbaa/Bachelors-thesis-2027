package io.github.r0mbaa.wms.layout.model;

import io.github.r0mbaa.wms.shared.marking.LocationCode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Планировка склада — единственный источник геометрии. Ячейки и граф выводятся из неё и
 * напрямую не редактируются: редактируется только план (§7.15.1), остальное производно.
 *
 * <p>Здесь проверяется только ссылочная целостность документа. Ошибки, которые пользователь
 * исправляет на плане (пересечения, ширина проходов, связность, FR-M15-07), проверяет
 * отдельный валидатор: он возвращает список замечаний с местом на плане, а не бросает исключение.
 *
 * @param version растёт при каждом изменении и входит в ключ кэша матрицы расстояний (§8.7, Д4)
 * @param width   размер склада по оси X, м
 * @param length  размер склада по оси Y, м
 * @param depot   точка старта и финиша маршрутов (§3.3)
 */
public record Layout(
        String warehouseCode,
        long version,
        double width,
        double length,
        Point depot,
        List<RackProfile> profiles,
        List<RackRow> rows) {

    public Layout {
        LocationCode.requireValidWarehouse(warehouseCode);
        if (!(width > 0) || !(length > 0) || !Double.isFinite(width) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Размеры склада должны быть положительными: " + width + " × " + length);
        }
        Objects.requireNonNull(depot, "Не задано положение депо");
        profiles = List.copyOf(profiles);
        rows = List.copyOf(rows);

        Set<String> profileNames = new HashSet<>();
        for (RackProfile profile : profiles) {
            if (!profileNames.add(profile.name())) {
                throw new IllegalArgumentException("Профиль стеллажа '" + profile.name() + "' объявлен дважды");
            }
        }
        Set<Integer> rowNumbers = new HashSet<>();
        for (RackRow row : rows) {
            if (!rowNumbers.add(row.number())) {
                throw new IllegalArgumentException("Номер ряда " + row.number() + " использован дважды");
            }
            for (String section : row.sections()) {
                if (!profileNames.contains(section)) {
                    throw new IllegalArgumentException("Ряд " + row.number() + " ссылается на неизвестный профиль '" + section + "'");
                }
            }
        }
    }

    public RackProfile profile(String name) {
        return profiles.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет профиля стеллажа '" + name + "'"));
    }

    /** Глубина габарита ряда: по самой глубокой секции. */
    public double rowDepth(RackRow row) {
        return row.sections().stream().mapToDouble(name -> profile(name).depth()).max().orElseThrow();
    }
}

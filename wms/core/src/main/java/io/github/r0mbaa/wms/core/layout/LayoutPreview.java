package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.layout.cells.Cell;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Что изменится в учёте, если сохранить планировку (FR-M15-03c): сколько ячеек появится и
 * исчезнет, и в каких исчезающих ячейках лежит товар. Сохранение такую планировку всё равно
 * отвергнет, а предпросмотр показывает это заранее, пока пользователь редактирует план.
 */
@Component
class LayoutPreview {

    private static final int SHOWN = 20;

    private final JdbcTemplate jdbc;

    LayoutPreview(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    CellChanges changes(long warehouseId, List<Cell> cells) {
        Set<String> current = new HashSet<>(jdbc.queryForList(
                "select code from location where warehouse_id = ? and row_no is not null and active",
                String.class, warehouseId));
        Set<String> next = new HashSet<>(cells.size());
        cells.forEach(cell -> next.add(cell.code().value()));

        int added = (int) next.stream().filter(code -> !current.contains(code)).count();
        List<String> removed = current.stream().filter(code -> !next.contains(code)).sorted().toList();
        List<String> removedWithStock = removed.isEmpty() ? List.of() : jdbc.queryForList("""
                select distinct l.code from location l join stock s on s.location_id = l.id
                where l.warehouse_id = ? and l.code = any(?) and s.quantity > 0
                order by l.code
                """, String.class, warehouseId, removed.toArray(String[]::new));
        return new CellChanges(cells.size(), added, removed.size(),
                removedWithStock.subList(0, Math.min(SHOWN, removedWithStock.size())), removedWithStock.size());
    }

    /**
     * @param removedWithStock      первые {@value #SHOWN} исчезающих ячеек с товаром
     * @param removedWithStockTotal сколько их всего
     */
    public record CellChanges(int cells, int added, int removed, List<String> removedWithStock, int removedWithStockTotal) {
    }
}

package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.layout.cells.Cell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Переносит ячейки, выведенные из планировки, в таблицу мест хранения.
 *
 * <p>Идёт мимо JPA пакетной вставкой с {@code ON CONFLICT}: склад в 20 тыс. ячеек поштучными
 * INSERT через Hibernate сохранялся бы секундами (NFR-P-08). Ячейка опознаётся по коду, а код
 * строится от неизменного номера ряда (§8.7, В2). Поэтому перенос ряда обновляет координаты
 * существующих ячеек, а их остатки и история остаются на месте.
 */
@Component
class CellMaterializer {

    private static final int BATCH_SIZE = 500;

    /** Зона, тип и блокировка существующей ячейки не трогаются: их задают вне конструктора. */
    private static final String UPSERT = """
            insert into location (warehouse_id, zone_id, code, type, row_no, section_no, level_no, position_no,
                                  x, y, z, access_x, access_y, facing, max_weight_kg, max_volume_m3,
                                  active, layout_version)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?)
            on conflict (code) do update set
                x = excluded.x, y = excluded.y, z = excluded.z,
                access_x = excluded.access_x, access_y = excluded.access_y, facing = excluded.facing,
                max_weight_kg = excluded.max_weight_kg, max_volume_m3 = excluded.max_volume_m3,
                active = true, layout_version = excluded.layout_version, version = location.version + 1
            """;

    /** Новая ячейка ряда попадает в зону, где уже лежит большинство ячеек этого ряда. */
    private static final String ROW_ZONES = """
            select distinct on (row_no) row_no, zone_id
            from location
            where warehouse_id = ? and row_no is not null and active and zone_id is not null
            group by row_no, zone_id
            order by row_no, count(*) desc, zone_id
            """;

    private static final String DEACTIVATE_VANISHED = """
            update location set active = false, version = version + 1
            where warehouse_id = ? and row_no is not null and active and layout_version <> ?
            """;

    private final JdbcTemplate jdbc;

    CellMaterializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param defaultZoneId зона для ячеек рядов, которых раньше не было
     */
    Result apply(long warehouseId, long layoutVersion, List<Cell> cells, long defaultZoneId) {
        Map<String, Boolean> existing = new HashMap<>();
        jdbc.query("select code, active from location where warehouse_id = ? and row_no is not null",
                rs -> {
                    existing.put(rs.getString("code"), rs.getBoolean("active"));
                }, warehouseId);
        Map<Integer, Long> rowZones = new HashMap<>();
        jdbc.query(ROW_ZONES, rs -> {
            rowZones.put(rs.getInt("row_no"), rs.getLong("zone_id"));
        }, warehouseId);
        Map<Long, String> zoneTypes = new HashMap<>();
        jdbc.query("select id, type from zone where warehouse_id = ?", rs -> {
            zoneTypes.put(rs.getLong("id"), rs.getString("type"));
        }, warehouseId);

        jdbc.batchUpdate(UPSERT, cells, BATCH_SIZE, (ps, cell) -> {
            long zoneId = rowZones.getOrDefault(cell.code().row(), defaultZoneId);
            ps.setLong(1, warehouseId);
            ps.setLong(2, zoneId);
            ps.setString(3, cell.code().value());
            ps.setString(4, zoneTypes.get(zoneId));
            ps.setInt(5, cell.code().row());
            ps.setInt(6, cell.code().section());
            ps.setInt(7, cell.code().level());
            ps.setInt(8, cell.code().position());
            ps.setDouble(9, cell.center().x());
            ps.setDouble(10, cell.center().y());
            ps.setDouble(11, cell.shelfHeight());
            ps.setDouble(12, cell.access().x());
            ps.setDouble(13, cell.access().y());
            ps.setString(14, cell.facing().name());
            ps.setDouble(15, cell.maxWeightKg());
            ps.setDouble(16, cell.volume());
            ps.setLong(17, layoutVersion);
        });
        int removed = jdbc.update(DEACTIVATE_VANISHED, warehouseId, layoutVersion);

        int added = (int) cells.stream()
                .filter(cell -> !existing.getOrDefault(cell.code().value(), false))
                .count();
        return new Result(cells.size(), added, removed);
    }

    /**
     * @param cells   ячеек в новой версии
     * @param added   появилось, в том числе вернулось после удаления
     * @param removed исчезло из планировки: помечены неактивными, но не удалены
     */
    record Result(int cells, int added, int removed) {
    }
}

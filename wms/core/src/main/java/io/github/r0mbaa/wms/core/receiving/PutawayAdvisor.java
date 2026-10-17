package io.github.r0mbaa.wms.core.receiving;

import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.layout.model.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Рекомендация ячейки для размещения (FR-M3-03, FR-M3-04). Это простое правило, а не
 * исследуемый алгоритм: слоттинг по ABC и совместной встречаемости (M12) заменит его позже.
 *
 * <p>Правило по убыванию приоритета:
 * <ol>
 *   <li>ячейка, где этот SKU уже лежит (консолидация), если выдержит добавку;</li>
 *   <li>пустая ячейка отбора или навала, ближайшая к депо по манхэттенской метрике, а для SKU
 *       класса C — самая дальняя: ближние места остаются ходовому товару.</li>
 * </ol>
 * В любом случае ячейка активна, не заблокирована, принимает класс хранения, выдержит вес и
 * вместит объём, а в зоне с выделенным местом не создаёт второе место этого SKU (INV-08).
 * Разные SKU в одну ячейку не рекомендуются, хотя фактическое размещение это допускает.
 */
@Component
class PutawayAdvisor {

    private static final String QUERY = """
            with load as (
                select s.location_id,
                       sum(s.quantity) as units,
                       sum(s.quantity * k.weight_kg) as weight,
                       sum(s.quantity * k.volume_m3) as volume,
                       bool_or(s.sku_id = :sku and s.quantity > 0) as holds_sku
                from stock s
                join sku k on k.id = s.sku_id
                join location sl on sl.id = s.location_id
                where sl.warehouse_id = :warehouse
                group by s.location_id
            )
            select l.code
            from location l
            left join zone z on z.id = l.zone_id
            left join load on load.location_id = l.id
            where l.warehouse_id = :warehouse and l.active and not l.blocked and l.row_no is not null
              and l.type in ('PICKING', 'BULK')
              and (l.allowed_storage_classes is null or position(:storageClass in l.allowed_storage_classes) > 0)
              and (l.max_weight_kg is null or coalesce(load.weight, 0) + :weight <= l.max_weight_kg + 1e-9)
              and (l.max_volume_m3 is null or coalesce(load.volume, 0) + :volume <= l.max_volume_m3 + 1e-9)
              and (coalesce(load.units, 0) = 0 or coalesce(load.holds_sku, false))
              and not (z.storage_policy = 'DEDICATED' and not coalesce(load.holds_sku, false) and exists (
                    select 1 from stock s2 join location l2 on l2.id = s2.location_id
                    where l2.zone_id = l.zone_id and s2.sku_id = :sku and s2.quantity > 0))
            order by coalesce(load.holds_sku, false) desc,
                     (abs(l.access_x - :depotX) + abs(l.access_y - :depotY)) * :direction,
                     l.code
            limit 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    PutawayAdvisor(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return код ячейки или пусто, если подходящей нет: склад полон или товар ни с чем не совместим */
    Optional<String> recommend(Warehouse warehouse, Sku sku, int quantity, Point depot) {
        List<String> found = jdbc.queryForList(QUERY, Map.of(
                "warehouse", warehouse.getId(),
                "sku", sku.getId(),
                "storageClass", sku.getStorageClass().name(),
                "weight", quantity * sku.getWeightKg(),
                "volume", quantity * sku.getVolumeM3(),
                "depotX", depot.x(),
                "depotY", depot.y(),
                "direction", "C".equals(sku.getAbcClass()) ? -1 : 1), String.class);
        return found.stream().findFirst();
    }
}

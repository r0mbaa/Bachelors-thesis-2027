package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.layout.LayoutSaved;
import io.github.r0mbaa.wms.core.topology.RowsAssignedToZone;
import io.github.r0mbaa.wms.core.topology.StoragePolicy;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Инварианты остатков, которые могут нарушить изменения топологии. Слушатели выполняются
 * синхронно в транзакции изменения, и исключение отменяет его целиком.
 */
@Component
class InventoryGuards {

    private static final int SHOWN = 5;

    private final JdbcTemplate jdbc;

    InventoryGuards(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** FR-M15-03c: ячейка с товаром не может исчезнуть из планировки. */
    @EventListener
    void onLayoutSaved(LayoutSaved event) {
        List<String> occupied = jdbc.queryForList("""
                select distinct l.code from location l join stock s on s.location_id = l.id
                where l.warehouse_id = ? and not l.active and s.quantity > 0
                order by l.code
                limit ?
                """, String.class, event.warehouseId(), SHOWN + 1);
        if (!occupied.isEmpty()) {
            throw new ConflictException("Новая версия планировки убирает ячейки, в которых лежит товар: "
                    + list(occupied) + ". Сначала переместите товар, затем сохраните планировку");
        }
    }

    /** INV-08: в зоне с выделенным местом SKU занимает не больше одной ячейки. */
    @EventListener
    void onRowsAssigned(RowsAssignedToZone event) {
        if (event.policy() != StoragePolicy.DEDICATED) {
            return;
        }
        List<String> spread = jdbc.queryForList("""
                select k.article from stock s
                join location l on l.id = s.location_id
                join sku k on k.id = s.sku_id
                where l.zone_id = ? and s.quantity > 0
                group by k.article having count(*) > 1
                order by k.article
                limit ?
                """, String.class, event.zoneId(), SHOWN + 1);
        if (!spread.isEmpty()) {
            throw new ConflictException("Зона " + event.zoneCode() + " работает с выделенным местом, а товары "
                    + list(spread) + " окажутся в ней в нескольких ячейках: сначала сведите каждый товар в одну "
                    + "ячейку, излишки перенесите в навал (INV-08)");
        }
    }

    private static String list(List<String> items) {
        String shown = String.join(", ", items.subList(0, Math.min(SHOWN, items.size())));
        return items.size() > SHOWN ? shown + " и другие" : shown;
    }
}

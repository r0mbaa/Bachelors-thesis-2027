package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.inventory.StockLedger.Posting;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.LocationType;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ручные операции с остатками и запросы к ним (M4). Товар и место принимаются так, как их ввёл
 * или отсканировал пользователь: QR, штрихкод, артикул, код ячейки.
 */
@Service
public class InventoryService {

    private final StockLedger ledger;
    private final CatalogService catalog;
    private final TopologyService topology;
    private final StockRepository stocks;
    private final MovementRepository movements;
    private final JdbcTemplate jdbc;

    InventoryService(StockLedger ledger, CatalogService catalog, TopologyService topology, StockRepository stocks,
            MovementRepository movements, JdbcTemplate jdbc) {
        this.ledger = ledger;
        this.catalog = catalog;
        this.topology = topology;
        this.stocks = stocks;
        this.movements = movements;
        this.jdbc = jdbc;
    }

    /**
     * Назначение товара в ячейку прямо из плана (FR-M15-13): товар появляется на складе сразу в
     * ячейке, минуя приёмку. Нужен для подготовки сценариев и сокращённого варианта §15.4.
     */
    @Transactional
    public Movement place(String skuCode, String locationCode, int quantity, String comment) {
        return ledger.post(new Posting(MovementType.RECEIPT, catalog.resolve(skuCode), quantity, null,
                storage(locationCode), new DocumentRef("PLACEMENT", null), comment));
    }

    /** Перемещение между ячейками (FR-M4-09, FR-M15-14, FR-M15-15). */
    @Transactional
    public Movement transfer(String skuCode, String fromCode, String toCode, int quantity, String comment) {
        return ledger.post(new Posting(MovementType.TRANSFER, catalog.resolve(skuCode), quantity,
                storage(fromCode), storage(toCode), DocumentRef.manual(), comment));
    }

    @Transactional
    public Movement adjust(String skuCode, String locationCode, int actualQuantity, String reason) {
        return ledger.adjustTo(storage(locationCode), catalog.resolve(skuCode), actualQuantity,
                new DocumentRef("ADJUSTMENT_ACT", null), requireReason(reason));
    }

    @Transactional
    public Movement writeOff(String skuCode, String locationCode, int quantity, String reason) {
        return ledger.post(new Posting(MovementType.WRITE_OFF, catalog.resolve(skuCode), quantity,
                storage(locationCode), null, new DocumentRef("WRITE_OFF_ACT", null), requireReason(reason)));
    }

    /**
     * Ручные операции допустимы только с ячейками и зоной приёмки. Тара и зона отгрузки держат
     * отобранное, но не отгруженное (INV-09), и меняются только сборкой и отгрузкой.
     */
    private Location storage(String code) {
        Location location = topology.resolveLocation(code);
        if (!location.isCell() && location.getType() != LocationType.RECEIVING) {
            throw new IllegalArgumentException("Место " + location.getCode() + " (" + location.getType()
                    + ") меняется только сборкой и отгрузкой: вручную работайте с ячейками и зоной приёмки");
        }
        return location;
    }

    @Transactional(readOnly = true)
    public List<Stock> stockAt(String locationCode) {
        return stocks.findPositiveAt(topology.resolveLocation(locationCode));
    }

    @Transactional(readOnly = true)
    public List<Stock> stockOf(String article) {
        return stocks.findPositiveOf(catalog.get(article));
    }

    @Transactional(readOnly = true)
    public Page<Stock> stockIn(String warehouseCode, int page, int size) {
        return stocks.findPositiveIn(topology.warehouse(warehouseCode), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Movement> historyOf(String article, int page, int size) {
        return movements.historyOf(catalog.get(article), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Movement> historyAt(String locationCode, int page, int size) {
        return movements.historyAt(topology.resolveLocation(locationCode), PageRequest.of(page, size));
    }

    /**
     * Проверка INV-04 (FR-M4-06): остаток, восстановленный воспроизведением журнала, должен
     * совпадать с таблицей остатков. Пустой список означает, что учёт цел.
     */
    @Transactional(readOnly = true)
    public List<Discrepancy> checkIntegrity() {
        return jdbc.query("""
                with replayed as (
                    select location_id, sku_id, sum(delta) as quantity
                    from (
                        select location_to as location_id, sku_id, quantity as delta
                        from movement where location_to is not null
                        union all
                        select location_from, sku_id, -quantity
                        from movement where location_from is not null
                    ) journal
                    group by location_id, sku_id
                )
                select l.code, k.article, coalesce(s.quantity, 0) as recorded, coalesce(r.quantity, 0) as replayed
                from stock s
                full join replayed r on r.location_id = s.location_id and r.sku_id = s.sku_id
                join location l on l.id = coalesce(s.location_id, r.location_id)
                join sku k on k.id = coalesce(s.sku_id, r.sku_id)
                where coalesce(s.quantity, 0) <> coalesce(r.quantity, 0)
                order by l.code, k.article
                """, (rs, n) -> new Discrepancy(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Укажите причину: она попадает в акт и в журнал движений");
        }
        return reason.strip();
    }

    /**
     * @param recorded остаток в таблице остатков
     * @param replayed остаток по журналу движений
     */
    public record Discrepancy(String location, String article, long recorded, long replayed) {
    }
}

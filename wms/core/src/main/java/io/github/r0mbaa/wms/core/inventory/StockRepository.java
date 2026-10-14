package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Строки остатка одного SKU, заблокированные на запись в порядке id места. Единый порядок
     * захвата исключает взаимную блокировку встречных перемещений (§12.2). Без join: иначе
     * блокировка легла бы и на строки мест хранения, на которые ссылаются вставляемые движения.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Stock s
            where s.sku = :sku and s.location.id in :locationIds
            order by s.location.id
            """)
    List<Stock> lockForUpdate(Sku sku, Collection<Long> locationIds);

    @Query("""
            select s from Stock s join fetch s.sku
            where s.location = :location and s.quantity > 0
            order by s.sku.article
            """)
    List<Stock> findPositiveAt(Location location);

    @Query("""
            select s from Stock s join fetch s.location l left join fetch l.zone
            where s.sku = :sku and s.quantity > 0
            order by l.code
            """)
    List<Stock> findPositiveOf(Sku sku);

    @Query(value = """
            select s from Stock s join fetch s.location l left join fetch l.zone join fetch s.sku
            where l.warehouse = :warehouse and s.quantity > 0
            order by l.code, s.sku.article
            """, countQuery = """
            select count(s) from Stock s where s.location.warehouse = :warehouse and s.quantity > 0
            """)
    Page<Stock> findPositiveIn(Warehouse warehouse, Pageable pageable);
}

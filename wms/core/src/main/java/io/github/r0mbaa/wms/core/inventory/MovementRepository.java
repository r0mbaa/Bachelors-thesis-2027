package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.topology.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    /** История перемещений SKU (FR-M4-05), новые сверху. */
    @Query(value = """
            select m from Movement m join fetch m.sku left join fetch m.locationFrom left join fetch m.locationTo
            where m.sku = :sku
            order by m.occurredAt desc, m.id desc
            """, countQuery = "select count(m) from Movement m where m.sku = :sku")
    Page<Movement> historyOf(Sku sku, Pageable pageable);

    /** История операций по месту хранения (FR-M4-05): и приход, и расход. */
    @Query(value = """
            select m from Movement m join fetch m.sku left join fetch m.locationFrom left join fetch m.locationTo
            where m.locationFrom = :location or m.locationTo = :location
            order by m.occurredAt desc, m.id desc
            """, countQuery = """
            select count(m) from Movement m where m.locationFrom = :location or m.locationTo = :location
            """)
    Page<Movement> historyAt(Location location, Pageable pageable);
}

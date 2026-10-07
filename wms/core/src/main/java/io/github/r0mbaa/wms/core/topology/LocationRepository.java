package io.github.r0mbaa.wms.core.topology;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @EntityGraph(attributePaths = {"zone", "warehouse"})
    Optional<Location> findByCode(String code);

    @EntityGraph(attributePaths = "zone")
    @Query("""
            select l from Location l left join l.zone z
            where l.warehouse = :warehouse
              and l.active = true
              and (:rowNo is null or l.rowNo = :rowNo)
              and (:zoneCode is null or z.code = :zoneCode)
              and (:type is null or l.type = :type)
              and (:blocked is null or l.blocked = :blocked)
            order by l.code
            """)
    Page<Location> search(Warehouse warehouse, Integer rowNo, String zoneCode, LocationType type, Boolean blocked,
            Pageable pageable);

    /** Зона задаёт и тип ячеек, поэтому тип переписывается вместе с ней. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Location l set l.zone = :zone, l.type = :type, l.version = l.version + 1
            where l.warehouse = :warehouse and l.rowNo in :rows
            """)
    int assignZone(Warehouse warehouse, Collection<Integer> rows, Zone zone, LocationType type);
}

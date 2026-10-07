package io.github.r0mbaa.wms.core.layout;

import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface LayoutVersionRepository extends JpaRepository<LayoutVersion, Long> {

    Optional<LayoutVersion> findByWarehouseAndVersion(Warehouse warehouse, long version);

    /** Список без самих документов: документ большого склада весит сотни килобайт. */
    @Query("""
            select new io.github.r0mbaa.wms.core.layout.LayoutVersionRepository$Summary(v.version, v.createdBy, v.createdAt)
            from LayoutVersion v where v.warehouse = :warehouse order by v.version desc
            """)
    List<Summary> summaries(Warehouse warehouse);

    record Summary(long version, String createdBy, Instant createdAt) {
    }
}

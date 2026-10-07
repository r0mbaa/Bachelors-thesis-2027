package io.github.r0mbaa.wms.core.topology;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    Optional<Zone> findByWarehouseAndCode(Warehouse warehouse, String code);

    List<Zone> findByWarehouseOrderByCode(Warehouse warehouse);
}

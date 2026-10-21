package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaveRepository extends JpaRepository<Wave, Long> {

    @EntityGraph(attributePaths = "warehouse")
    Optional<Wave> findByNumber(String number);

    @EntityGraph(attributePaths = "warehouse")
    List<Wave> findByWarehouseOrderByCreatedAtDesc(Warehouse warehouse);
}

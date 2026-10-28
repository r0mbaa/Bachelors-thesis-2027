package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    @EntityGraph(attributePaths = {"warehouse", "user", "currentLocation"})
    Optional<Worker> findByCode(String code);

    @EntityGraph(attributePaths = {"warehouse", "user", "currentLocation"})
    Optional<Worker> findByUserUsername(String username);

    boolean existsByCode(String code);

    boolean existsByUserUsername(String username);

    @EntityGraph(attributePaths = {"user", "currentLocation"})
    List<Worker> findByWarehouseOrderByCode(Warehouse warehouse);
}

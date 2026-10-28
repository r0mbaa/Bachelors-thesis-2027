package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ContainerRepository extends JpaRepository<Container, Long> {

    @EntityGraph(attributePaths = {"warehouse", "location"})
    Optional<Container> findByCode(String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"warehouse", "location"})
    List<Container> findByWarehouseOrderByCode(Warehouse warehouse);

    /** Одна тара не может уйти в два задания одновременно. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Container c join fetch c.location join fetch c.warehouse where c.code = :code")
    Optional<Container> lockByCode(String code);
}

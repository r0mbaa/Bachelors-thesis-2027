package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = {"lines", "lines.sku", "warehouse", "wave"})
    Optional<CustomerOrder> findByNumber(String number);

    boolean existsByNumber(String number);

    @EntityGraph(attributePaths = {"warehouse", "wave"})
    @Query("""
            select o from CustomerOrder o
            where o.warehouse = :warehouse and (:status is null or o.status = :status)
            order by o.createdAt desc, o.id desc
            """)
    Page<CustomerOrder> search(Warehouse warehouse, OrderStatus status, Pageable pageable);

    /**
     * Заказы, подходящие в волну, заблокированные на запись: два диспетчера, формирующие волны
     * одновременно, не заберут один заказ дважды. Ближайший дедлайн первым, затем приоритет.
     *
     * <p>Фильтр по дедлайну включается флагом, а не {@code null}: PostgreSQL не выводит тип
     * параметра времени в выражении {@code ? is null}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from CustomerOrder o
            where o.warehouse = :warehouse and o.status = :status and o.wave is null and o.hot = false
              and (:anyDeadline = true or o.deadlineAt <= :deadlineBefore)
              and (:carrier is null or o.carrier = :carrier)
              and (:direction is null or o.direction = :direction)
              and o.priority >= :minPriority
            order by o.deadlineAt asc nulls last, o.priority desc, o.createdAt, o.id
            """)
    List<CustomerOrder> lockWaveCandidates(Warehouse warehouse, OrderStatus status, boolean anyDeadline,
            Instant deadlineBefore, String carrier, String direction, int minPriority, Pageable limit);

    @EntityGraph(attributePaths = {"lines", "lines.sku", "warehouse", "wave"})
    @Query("""
            select o from CustomerOrder o where o.wave = :wave
            order by o.deadlineAt asc nulls last, o.priority desc, o.createdAt, o.id
            """)
    List<CustomerOrder> findInWave(Wave wave);
}

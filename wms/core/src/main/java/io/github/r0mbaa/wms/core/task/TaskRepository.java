package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"warehouse", "worker", "batch", "batch.container"})
    Optional<Task> findByNumber(String number);

    @EntityGraph(attributePaths = {"worker", "batch", "batch.container"})
    @Query("""
            select t from Task t
            where t.warehouse = :warehouse and t.status in :statuses
            order by t.deadlineAt asc nulls last, t.priority desc, t.createdAt, t.id
            """)
    List<Task> findByStatuses(Warehouse warehouse, Collection<TaskStatus> statuses);

    boolean existsByBatchContainerAndStatusIn(Container container, Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"warehouse", "worker", "batch", "batch.container"})
    List<Task> findByWorkerAndStatusIn(Worker worker, Collection<TaskStatus> statuses);

    /** Шаги заказа в заданиях с указанными статусами. */
    @Query("""
            select s from TaskStep s join fetch s.task t
            where s.orderLine.order = :order and t.status in :statuses
            """)
    List<TaskStep> stepsOf(CustomerOrder order, Collection<TaskStatus> statuses);
}

package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

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

    /**
     * Очередь заданий по раннему дедлайну (EDF), затем по приоритету. {@code SKIP LOCKED}: два
     * сборщика, одновременно запросившие задание, получают разные, а не ждут друг друга.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select t from Task t
            where t.warehouse = :warehouse and t.status = :status
            order by t.deadlineAt asc nulls last, t.priority desc, t.createdAt, t.id
            """)
    List<Task> lockQueued(Warehouse warehouse, TaskStatus status, Pageable limit);

    /**
     * События терминала по одному заданию обрабатываются по очереди. Блокировка берётся до
     * загрузки задания: иначе запрос вернул бы уже загруженный экземпляр без свежего состояния.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> lockById(long id);

    @Query("select s.task.id from TaskStep s where s.id = :stepId")
    Optional<Long> taskIdOfStep(long stepId);

    @Query("select t.id from Task t where t.worker = :worker and t.status in :statuses")
    List<Long> heldTaskIds(Worker worker, Collection<TaskStatus> statuses);

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

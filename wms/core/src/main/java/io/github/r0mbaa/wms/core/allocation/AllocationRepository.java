package io.github.r0mbaa.wms.core.allocation;

import io.github.r0mbaa.wms.core.order.CustomerOrder;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    @Query("""
            select a from Allocation a join fetch a.orderLine l join fetch a.location join fetch a.sku
            where l.order = :order
            order by l.lineNo, a.id
            """)
    List<Allocation> findByOrder(CustomerOrder order);

    @Query("""
            select a from Allocation a join fetch a.orderLine l join fetch a.location join fetch a.sku
            where l.order = :order and a.status = io.github.r0mbaa.wms.core.allocation.AllocationStatus.RESERVED
            order by l.lineNo, a.id
            """)
    List<Allocation> findActive(CustomerOrder order);

    /** Заказы, у которых истёк срок хотя бы одного резерва. */
    @Query("""
            select distinct l.order.id from Allocation a join a.orderLine l
            where a.status = io.github.r0mbaa.wms.core.allocation.AllocationStatus.RESERVED and a.expiresAt < :now
            """)
    List<Long> ordersWithExpired(Instant now);

}

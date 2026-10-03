package io.github.r0mbaa.wms.core.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            select e from AuditEntry e
            where (:entityType is null or e.entityType = :entityType)
              and (:entityId is null or e.entityId = :entityId)
            order by e.occurredAt desc, e.id desc
            """)
    Page<AuditEntry> search(String entityType, String entityId, Pageable pageable);
}

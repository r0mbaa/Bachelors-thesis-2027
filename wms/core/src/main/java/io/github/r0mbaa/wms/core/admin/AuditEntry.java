package io.github.r0mbaa.wms.core.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Запись журнала аудита: только вставка. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant occurredAt;

    private String actor;

    private String action;

    private String entityType;

    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    private String newValue;

    protected AuditEntry() {
    }

    AuditEntry(Instant occurredAt, String actor, String action, String entityType, String entityId,
            String oldValue, String newValue) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }
}

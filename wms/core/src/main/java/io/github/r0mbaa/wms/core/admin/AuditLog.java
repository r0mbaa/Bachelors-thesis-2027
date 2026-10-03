package io.github.r0mbaa.wms.core.admin;

import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Журнал аудита (FR-M13-02, NFR-SEC-03): кто, что, когда, старое и новое значение.
 *
 * <p>Запись делается в транзакции самого изменения: если изменение откатилось, записи о нём
 * тоже нет.
 */
@Service
public class AuditLog {

    private final AuditEntryRepository entries;
    private final CurrentUser currentUser;
    private final JsonMapper json;
    private final Clock clock;

    AuditLog(AuditEntryRepository entries, CurrentUser currentUser, JsonMapper json, Clock clock) {
        this.entries = entries;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param action     что сделано, константа вида {@code SKU_UPDATED}
     * @param entityType тип сущности, константа вида {@code SKU}
     * @param entityId   человекочитаемый ключ: артикул, код ячейки, имя пользователя
     * @param oldValue   значение до изменения; сериализуется в JSON, {@code null} — не было
     * @param newValue   значение после изменения; сериализуется в JSON, {@code null} — не стало
     */
    @Transactional
    public void record(String action, String entityType, Object entityId, Object oldValue, Object newValue) {
        entries.save(new AuditEntry(clock.instant(), currentUser.username(), action, entityType,
                entityId == null ? null : entityId.toString(), toJson(oldValue), toJson(newValue)));
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(String entityType, String entityId, int page, int size) {
        return entries.search(entityType, entityId, PageRequest.of(page, size));
    }

    private String toJson(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }
}

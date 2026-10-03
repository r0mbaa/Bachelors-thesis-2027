package io.github.r0mbaa.wms.core.admin;

import io.github.r0mbaa.wms.core.common.PageResponse;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'WAREHOUSE_ADMIN')")
class AuditController {

    private final AuditLog audit;
    private final JsonMapper json;

    AuditController(AuditLog audit, JsonMapper json) {
        this.audit = audit;
        this.json = json;
    }

    @GetMapping
    PageResponse<AuditEntryView> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(audit.search(entityType, entityId, page, PageResponse.clampSize(size)), this::view);
    }

    private AuditEntryView view(AuditEntry e) {
        return new AuditEntryView(e.getOccurredAt(), e.getActor(), e.getAction(), e.getEntityType(), e.getEntityId(),
                parse(e.getOldValue()), parse(e.getNewValue()));
    }

    private JsonNode parse(String value) {
        return value == null ? null : json.readTree(value);
    }

    /** Значения отдаются как вложенный JSON, а не строкой: веб-панель показывает их без разбора. */
    record AuditEntryView(Instant occurredAt, String actor, String action, String entityType, String entityId,
            JsonNode oldValue, JsonNode newValue) {
    }
}

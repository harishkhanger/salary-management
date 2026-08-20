package com.acme.salary.events;

import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import lombok.Builder;

import java.util.Map;

/**
 * Domain event published inside the business transaction; AuditTrailListener
 * persists it AFTER_COMMIT. changedFields (old -> new per field) is only for
 * profile/settings edits — money events carry a thin reference instead.
 */
@Builder
public record AuditEvent(
        AuditEntityType entityType,
        Long entityId,
        AuditAction action,
        String actor,
        Map<String, Object> changedFields,
        String refTable,
        Long refId,
        Long runId
) {
}

package com.acme.salary.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Keyset-paginated audit feed. kind=ENTRY is a plain event; kind=RUN is a
 * collapsed header standing in for a whole bulk operation — expand it by
 * calling the same endpoint with runId (+ runType).
 */
public record AuditFeedResponse(List<AuditFeedItem> items, String nextCursor) {

    public record AuditFeedItem(
            String kind,
            Long id,
            String entityType,
            Long entityId,
            String action,
            String actor,
            Object changedFields,
            String refTable,
            Long refId,
            Long runId,
            Map<String, Object> runSummary,
            LocalDateTime createdAt
    ) {
    }
}

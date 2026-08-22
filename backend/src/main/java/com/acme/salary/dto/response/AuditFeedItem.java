package com.acme.salary.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One audit feed row. kind=ENTRY is a plain event; kind=RUN is a collapsed
 * header standing in for a whole bulk operation — expand it by calling the
 * same endpoint with runId (+ runType). Served inside the shared PageResponse.
 */
public record AuditFeedItem(
        String kind,
        Long id,
        String entityType,
        Long entityId,
        /** Employee name for EMPLOYEE rows (deleted employees included) so the feed can speak in names. */
        String entityName,
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

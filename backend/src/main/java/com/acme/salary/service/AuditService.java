package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.response.AuditFeedResponse;
import com.acme.salary.dto.response.AuditFeedResponse.AuditFeedItem;
import com.acme.salary.entities.AuditLog;
import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.entities.PayrollRun;
import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import com.acme.salary.exception.ValidationException;
import com.acme.salary.repository.AuditLogRepository;
import com.acme.salary.repository.BulkRaiseRunRepository;
import com.acme.salary.repository.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The global audit feed. Keyset pagination over (created_at DESC, id DESC) —
 * constant-time at any depth, never OFFSET. The global view hides run-tagged
 * item rows and shows RUN_COMPLETED headers inline (approach b: headers are
 * audit rows too); expanding a run is the same query filtered by runId.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final BulkRaiseRunRepository bulkRaiseRunRepository;
    private final PaginationProperties paginationProperties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AuditFeedResponse feed(String cursor, int limit, String entityType, Long entityId,
                                  Long runId, String runType, String action, String actor,
                                  LocalDate from, LocalDate to) {
        int pageSize = paginationProperties.clampSize(limit);
        Specification<AuditLog> spec = filterSpec(entityType, entityId, runId, runType)
                .and(refinementSpec(action, actor, from, to));
        if (cursor != null && !cursor.isBlank()) {
            spec = spec.and(keysetAfter(decodeCursor(cursor)));
        }

        // fetch one extra row to know whether a next page exists
        List<AuditLog> rows = auditLogRepository.findAll(spec,
                PageRequest.of(0, pageSize + 1,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).getContent();
        boolean hasMore = rows.size() > pageSize;
        List<AuditLog> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<Long, Map<String, Object>> runSummaries = loadRunSummaries(page);
        List<AuditFeedItem> items = page.stream().map(row -> toItem(row, runSummaries)).toList();
        String nextCursor = hasMore ? encodeCursor(page.getLast()) : null;
        return new AuditFeedResponse(items, nextCursor);
    }

    /**
     * Global: plain rows + run headers, run items hidden. Entity view: that
     * entity's full activity, run items included. Run view: that run's items;
     * runType narrows to the run's own actions because payroll and bulk-raise
     * run ids come from different tables and may collide.
     */
    private Specification<AuditLog> filterSpec(String entityType, Long entityId,
                                               Long runId, String runType) {
        if (entityType != null) {
            requireOneOf(entityType, AuditEntityType.values(), "entityType");
        }
        if (entityType != null && entityId != null) {
            return (root, q, cb) -> cb.and(
                    cb.equal(root.get("entityType"), entityType),
                    cb.equal(root.get("entityId"), entityId));
        }
        if (runId != null) {
            Specification<AuditLog> spec = (root, q, cb) -> cb.and(
                    cb.equal(root.get("runId"), runId),
                    cb.notEqual(root.get("action"), AuditAction.RUN_COMPLETED.name()));
            if ("PAYROLL".equals(runType)) {
                spec = spec.and((root, q, cb) ->
                        cb.equal(root.get("action"), AuditAction.SALARY_CREDITED.name()));
            } else if ("BULK_RAISE".equals(runType)) {
                spec = spec.and((root, q, cb) -> root.get("action").in(
                        AuditAction.SALARY_CHANGED.name(), AuditAction.RAISE_PARKED.name(),
                        AuditAction.RAISE_APPROVED.name(), AuditAction.RAISE_REJECTED.name()));
            }
            return spec;
        }
        Specification<AuditLog> collapsed = (root, q, cb) -> cb.or(
                cb.isNull(root.get("runId")),
                cb.equal(root.get("action"), AuditAction.RUN_COMPLETED.name()));
        if (entityType != null) {
            // entityType without entityId narrows the global (collapsed) feed
            collapsed = collapsed.and((root, q, cb) ->
                    cb.equal(root.get("entityType"), entityType));
        }
        return collapsed;
    }

    /**
     * User-facing feed filters; conjunctive, so they compose with any base view
     * and with the keyset cursor unchanged. Dates are inclusive whole days.
     */
    private Specification<AuditLog> refinementSpec(String action, String actor,
                                                   LocalDate from, LocalDate to) {
        Specification<AuditLog> spec = (root, q, cb) -> cb.conjunction();
        if (action != null) {
            requireOneOf(action, AuditAction.values(), "action");
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
        }
        if (actor != null && !actor.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("actor"), actor));
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException("'to' date " + to + " is before 'from' date " + from);
        }
        if (from != null) {
            spec = spec.and((root, q, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) ->
                    cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
        }
        return spec;
    }

    private void requireOneOf(String value, Enum<?>[] allowed, String param) {
        for (Enum<?> candidate : allowed) {
            if (candidate.name().equals(value)) {
                return;
            }
        }
        throw new ValidationException("Unknown " + param + " '" + value + "'");
    }

    private Specification<AuditLog> keysetAfter(Cursor cursor) {
        return (root, q, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), cursor.createdAt()),
                cb.and(cb.equal(root.get("createdAt"), cursor.createdAt()),
                        cb.lessThan(root.get("id"), cursor.id())));
    }

    /** Batch-load run rows referenced by the page's RUN_COMPLETED headers. */
    private Map<Long, Map<String, Object>> loadRunSummaries(List<AuditLog> page) {
        Map<Long, Map<String, Object>> summaries = new LinkedHashMap<>();
        List<Long> payrollIds = headerIds(page, AuditEntityType.PAYROLL_RUN);
        List<Long> bulkIds = headerIds(page, AuditEntityType.BULK_RAISE_RUN);
        payrollRunRepository.findAllById(payrollIds).forEach(run ->
                summaries.put(headerKey(AuditEntityType.PAYROLL_RUN, run.getId()), Map.of(
                        "runType", "PAYROLL", "year", run.getYear(), "month", run.getMonth(),
                        "processedCount", run.getProcessedCount(),
                        "skippedHeldCount", run.getSkippedHeldCount(),
                        "alreadyProcessedCount", run.getAlreadyProcessedCount())));
        bulkRaiseRunRepository.findAllById(bulkIds).forEach(run ->
                summaries.put(headerKey(AuditEntityType.BULK_RAISE_RUN, run.getId()), Map.of(
                        "runType", "BULK_RAISE", "raiseType", run.getRaiseType().name(),
                        "raiseValue", run.getRaiseValue(),
                        "appliedCount", run.getAppliedCount(),
                        "reviewCount", run.getReviewCount(),
                        "excludedCount", run.getExcludedCount())));
        return summaries;
    }

    private List<Long> headerIds(List<AuditLog> page, AuditEntityType type) {
        return page.stream()
                .filter(row -> AuditAction.RUN_COMPLETED.name().equals(row.getAction())
                        && type.name().equals(row.getEntityType()))
                .map(AuditLog::getEntityId)
                .toList();
    }

    private Long headerKey(AuditEntityType type, Long runId) {
        // payroll and bulk ids may collide; namespace them in the lookup key
        return type == AuditEntityType.PAYROLL_RUN ? runId : -runId;
    }

    private AuditFeedItem toItem(AuditLog row, Map<Long, Map<String, Object>> runSummaries) {
        boolean isRunHeader = AuditAction.RUN_COMPLETED.name().equals(row.getAction());
        Map<String, Object> summary = null;
        if (isRunHeader) {
            AuditEntityType type = AuditEntityType.valueOf(row.getEntityType());
            summary = runSummaries.get(headerKey(type, row.getEntityId()));
        }
        Object changedFields = row.getChangedFields() == null ? null
                : objectMapper.readValue(row.getChangedFields(), Map.class);
        return new AuditFeedItem(isRunHeader ? "RUN" : "ENTRY", row.getId(),
                row.getEntityType(), row.getEntityId(), row.getAction(), row.getActor(),
                changedFields, row.getRefTable(), row.getRefId(), row.getRunId(),
                summary, row.getCreatedAt());
    }

    private record Cursor(LocalDateTime createdAt, Long id) {
    }

    private String encodeCursor(AuditLog last) {
        return Base64.getUrlEncoder().encodeToString(
                (last.getCreatedAt() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8).split("\\|");
            return new Cursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new ValidationException("Malformed cursor");
        }
    }
}

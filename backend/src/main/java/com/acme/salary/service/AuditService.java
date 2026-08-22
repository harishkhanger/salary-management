package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.response.AuditFeedItem;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.entities.AuditLog;
import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.entities.PayrollRun;
import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import com.acme.salary.exception.ValidationException;
import com.acme.salary.repository.AuditLogRepository;
import com.acme.salary.repository.BulkRaiseRunRepository;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The global audit feed. Offset-paginated over (created_at DESC, id DESC) like
 * every other list, so the UI shows numbered pages with a total; the id
 * tiebreaker keeps page boundaries stable inside equal-timestamp groups. The
 * global view hides run-tagged item rows and shows RUN_COMPLETED headers
 * inline (approach b: headers are audit rows too); expanding a run is the same
 * query filtered by runId. Keyset was the original design (constant-time at
 * any depth); numbered pages were chosen for UX consistency — the cost is a
 * COUNT plus an index walk that grows linearly with depth (~15ms at offset
 * 100k on 140k rows), documented in docs/DATABASE-DESIGN.md.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final BulkRaiseRunRepository bulkRaiseRunRepository;
    private final EmployeeRepository employeeRepository;
    private final PaginationProperties paginationProperties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<AuditFeedItem> feed(int page, int size, String entityType, Long entityId,
                                            Long runId, String runType, String action,
                                            String actor, LocalDate from, LocalDate to) {
        Specification<AuditLog> spec = filterSpec(entityType, entityId, runId, runType)
                .and(refinementSpec(action, actor, from, to));
        PageRequest pageRequest = PageRequest.of(paginationProperties.clampPage(page),
                paginationProperties.clampSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<AuditLog> rows = auditLogRepository.findAll(spec, pageRequest);
        Map<Long, Map<String, Object>> runSummaries = loadRunSummaries(rows.getContent());
        Map<Long, String> employeeNames = loadEmployeeNames(rows.getContent());
        return PageResponse.from(rows, row -> toItem(row, runSummaries, employeeNames));
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
     * and the page window. Dates are inclusive whole days.
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

    /** Batch-load names for the page's EMPLOYEE rows — one query, deleted employees included. */
    private Map<Long, String> loadEmployeeNames(List<AuditLog> page) {
        List<Long> ids = page.stream()
                .filter(row -> AuditEntityType.EMPLOYEE.name().equals(row.getEntityType()))
                .map(AuditLog::getEntityId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        employeeRepository.findAllById(ids).forEach(e -> names.put(e.getId(), e.getName()));
        return names;
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

    private AuditFeedItem toItem(AuditLog row, Map<Long, Map<String, Object>> runSummaries,
                                 Map<Long, String> employeeNames) {
        boolean isRunHeader = AuditAction.RUN_COMPLETED.name().equals(row.getAction());
        Map<String, Object> summary = null;
        if (isRunHeader) {
            AuditEntityType type = AuditEntityType.valueOf(row.getEntityType());
            summary = runSummaries.get(headerKey(type, row.getEntityId()));
        }
        Object changedFields = row.getChangedFields() == null ? null
                : objectMapper.readValue(row.getChangedFields(), Map.class);
        return new AuditFeedItem(isRunHeader ? "RUN" : "ENTRY", row.getId(),
                row.getEntityType(), row.getEntityId(), employeeNames.get(row.getEntityId()),
                row.getAction(), row.getActor(),
                changedFields, row.getRefTable(), row.getRefId(), row.getRunId(),
                summary, row.getCreatedAt());
    }
}

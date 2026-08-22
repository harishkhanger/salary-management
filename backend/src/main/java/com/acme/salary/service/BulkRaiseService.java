package com.acme.salary.service;

import com.acme.salary.config.JobProperties;
import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.request.BulkRaiseExecuteRequest;
import com.acme.salary.dto.request.BulkRaisePreviewRequest;
import com.acme.salary.dto.response.BulkRaisePreviewResponse;
import com.acme.salary.dto.response.BulkRaisePreviewResponse.CostImpactEntry;
import com.acme.salary.dto.response.BulkRaiseRunResponse;
import com.acme.salary.dto.CurrencyCohortAggregate;
import com.acme.salary.dto.OverThresholdAggregate;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.response.OverThresholdEmployee;
import com.acme.salary.dto.response.SalaryChangeOutcome;
import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.enums.RaiseType;
import com.acme.salary.events.AuditEvent;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.BulkRaiseRunRepository;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.OrgSettingsRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bulk raises run as a durable background job (outbox-style): queue() persists
 * the run row as the job record and returns immediately; BulkRaiseRunPoller
 * picks it up and drives processRun(). Progress and resume state are DERIVED
 * from the append-only ledger — salary_changes and raise_review_items tagged
 * with the run id — so a crash loses nothing and resumes exactly where it died.
 * Deliberately NOT @Transactional: each item runs in its own REQUIRES_NEW
 * transaction via BulkRaiseItemProcessor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkRaiseService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final EmployeeRepository employeeRepository;
    private final SalaryChangeRepository salaryChangeRepository;
    private final RaiseReviewItemRepository raiseReviewItemRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final BulkRaiseRunRepository bulkRaiseRunRepository;
    private final BulkRaiseItemProcessor itemProcessor;
    private final OrgSettingsRepository orgSettingsRepository;
    private final JobProperties jobProperties;
    private final PaginationProperties paginationProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Dry run: cost impact from per-currency SQL aggregates (10k rows are
     * never loaded), plus employees already over the guardrail threshold
     * across their whole history, flagged for optional exclusion.
     */
    public BulkRaisePreviewResponse preview(BulkRaisePreviewRequest request) {
        List<Long> picked = request.employeeIdsOrEmpty();
        List<CurrencyCohortAggregate> aggregates = picked.isEmpty()
                ? employeeRepository.aggregateCohortByCurrency(blankToNull(request.filterCountry()),
                        blankToNull(request.filterDepartment()))
                : employeeRepository.aggregateCohortByCurrencyIn(picked);
        Map<String, BigDecimal> usdRates = currencyRateRepository.findAll().stream()
                .collect(Collectors.toMap(CurrencyRate::getCode, CurrencyRate::getUsdRate));

        long affectedCount = 0;
        BigDecimal usdDelta = BigDecimal.ZERO;
        BigDecimal usdCurrent = BigDecimal.ZERO;
        List<CostImpactEntry> costImpact = new java.util.ArrayList<>();
        for (CurrencyCohortAggregate aggregate : aggregates) {
            BigDecimal proposed = proposedTotal(aggregate, request.raiseType(), request.value());
            BigDecimal delta = proposed.subtract(aggregate.totalSalary());
            costImpact.add(new CostImpactEntry(aggregate.currencyCode(),
                    aggregate.totalSalary(), proposed, delta));
            affectedCount += aggregate.headcount();
            // usd_rate convention: local units per 1 USD -> USD = local / rate
            usdDelta = usdDelta.add(delta.divide(usdRates.get(aggregate.currencyCode()),
                    2, RoundingMode.HALF_UP));
            usdCurrent = usdCurrent.add(aggregate.totalSalary().divide(usdRates.get(aggregate.currencyCode()),
                    2, RoundingMode.HALF_UP));
        }

        BigDecimal threshold = orgSettingsRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("org_settings row missing"))
                .getRaiseThresholdPercent();
        // a hand-picked cohort ignores the filters; the flag list is narrowed to the picked ids
        Set<Long> pickedSet = new HashSet<>(picked);
        List<OverThresholdEmployee> overThreshold = salaryChangeRepository.findOverThreshold(
                        picked.isEmpty() ? blankToNull(request.filterCountry()) : null,
                        picked.isEmpty() ? blankToNull(request.filterDepartment()) : null, threshold)
                .stream()
                .filter(a -> picked.isEmpty() || pickedSet.contains(a.employeeId()))
                .map(this::toOverThreshold).toList();

        return new BulkRaisePreviewResponse(affectedCount, costImpact, usdDelta, usdCurrent,
                usdCurrent.add(usdDelta), overThreshold);
    }

    /** Persists the job record and returns immediately (202); the poller executes it. */
    public BulkRaiseRunResponse queue(BulkRaiseExecuteRequest request, String actor) {
        BulkRaiseRun run = bulkRaiseRunRepository.save(BulkRaiseRun.builder()
                .raiseType(request.raiseType())
                .raiseValue(request.value())
                .filterCountry(blankToNull(request.filterCountry()))
                .filterDepartment(blankToNull(request.filterDepartment()))
                .excludedIds(toJson(request.excludedEmployeeIdsOrEmpty()))
                .employeeIds(toJson(request.employeeIdsOrEmpty()))
                .initiatedBy(actor)
                .status(JobStatus.QUEUED)
                .createdAt(LocalDateTime.now(clock))
                .build());
        return BulkRaiseRunResponse.from(run);
    }

    /**
     * Executes (or resumes) one run. Already-processed employees are those
     * with a change or review item tagged with this run id — the ledger is
     * the progress record, so re-entry after a crash never double-applies.
     */
    public void processRun(BulkRaiseRun run) {
        run.setStatus(JobStatus.RUNNING);
        bulkRaiseRunRepository.save(run);

        // hand-picked cohort (persisted on the run) or the filter-based one
        List<Long> picked = fromJson(run.getEmployeeIds());
        List<Long> cohortIds = picked.isEmpty()
                ? employeeRepository.findCohortIds(run.getFilterCountry(), run.getFilterDepartment())
                : employeeRepository.findCohortIdsIn(picked);
        Set<Long> excluded = new HashSet<>(fromJson(run.getExcludedIds()));
        Set<Long> alreadyProcessed = new HashSet<>(
                salaryChangeRepository.findEmployeeIdsByRun(run.getId()));
        alreadyProcessed.addAll(raiseReviewItemRepository.findEmployeeIdsByRun(run.getId()));

        int applied = salaryChangeRepository.findEmployeeIdsByRun(run.getId()).size();
        int parked = alreadyProcessed.size() - applied;
        int excludedCount = (int) cohortIds.stream().filter(excluded::contains).count();
        ChangeType changeType = toChangeType(run.getRaiseType());

        int sinceProgressUpdate = 0;
        for (Long employeeId : cohortIds) {
            if (excluded.contains(employeeId) || alreadyProcessed.contains(employeeId)) {
                continue;
            }
            try {
                SalaryChangeOutcome outcome = itemProcessor.process(employeeId, changeType,
                        run.getRaiseValue(), run.getId(), run.getInitiatedBy());
                if (outcome.status() == SalaryChangeOutcome.Status.APPLIED) {
                    applied++;
                } else {
                    parked++;
                }
            } catch (RuntimeException e) {
                log.warn("Bulk raise run {} item failed for employee {}: {}",
                        run.getId(), employeeId, e.getMessage());
            }
            if (++sinceProgressUpdate >= jobProperties.progressUpdateEvery()) {
                updateCounts(run, applied, parked, excludedCount);
                sinceProgressUpdate = 0;
            }
        }

        updateCounts(run, applied, parked, excludedCount);
        run.setStatus(JobStatus.COMPLETED);
        bulkRaiseRunRepository.save(run);
        eventPublisher.publishEvent(AuditEvent.builder()
                .entityType(AuditEntityType.BULK_RAISE_RUN).entityId(run.getId())
                .action(AuditAction.RUN_COMPLETED).actor(run.getInitiatedBy())
                .refTable("bulk_raise_runs").refId(run.getId())
                .runId(run.getId()).build());
        log.info("Bulk raise run {} completed: applied={}, review={}, excluded={}",
                run.getId(), applied, parked, excludedCount);
    }

    public BulkRaiseRunResponse getRun(Long id) {
        return BulkRaiseRunResponse.from(bulkRaiseRunRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Bulk raise run not found: " + id)));
    }

    public PageResponse<BulkRaiseRunResponse> listRuns(int page, int size) {
        return PageResponse.from(bulkRaiseRunRepository.findAll(
                        PageRequest.of(paginationProperties.clampPage(page),
                                paginationProperties.clampSize(size),
                                Sort.by("createdAt").descending())),
                BulkRaiseRunResponse::from);
    }

    private void updateCounts(BulkRaiseRun run, int applied, int parked, int excludedCount) {
        run.setAppliedCount(applied);
        run.setReviewCount(parked);
        run.setExcludedCount(excludedCount);
        bulkRaiseRunRepository.save(run);
    }

    /** total raise % = (current - baseline) / baseline, same arithmetic as the guardrail. */
    private OverThresholdEmployee toOverThreshold(OverThresholdAggregate a) {
        BigDecimal percent = a.currentSalary().subtract(a.baselineSalary())
                .multiply(HUNDRED).divide(a.baselineSalary(), 2, RoundingMode.HALF_UP);
        return new OverThresholdEmployee(a.employeeId(), a.employeeCode(), a.name(), percent, a.lastRaiseAt());
    }

    private BigDecimal proposedTotal(CurrencyCohortAggregate aggregate,
                                     RaiseType raiseType, BigDecimal value) {
        if (raiseType == RaiseType.PERCENT) {
            BigDecimal raise = aggregate.totalSalary().multiply(value)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            return aggregate.totalSalary().add(raise);
        }
        return aggregate.totalSalary()
                .add(value.multiply(BigDecimal.valueOf(aggregate.headcount())));
    }

    private ChangeType toChangeType(RaiseType raiseType) {
        return raiseType == RaiseType.PERCENT ? ChangeType.PERCENT : ChangeType.AMOUNT;
    }

    private String toJson(List<Long> ids) {
        try {
            return ids.isEmpty() ? null : objectMapper.writeValueAsString(ids);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize excluded ids", e);
        }
    }

    private List<Long> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not parse excluded ids: " + json, e);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

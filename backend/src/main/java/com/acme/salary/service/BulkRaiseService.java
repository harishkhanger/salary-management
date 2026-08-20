package com.acme.salary.service;

import com.acme.salary.config.BulkRaiseProperties;
import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.PageResponse;
import com.acme.salary.dto.BulkRaiseExecuteRequest;
import com.acme.salary.dto.BulkRaisePreviewRequest;
import com.acme.salary.dto.BulkRaisePreviewResponse;
import com.acme.salary.dto.BulkRaisePreviewResponse.CostImpactEntry;
import com.acme.salary.dto.BulkRaiseRunResponse;
import com.acme.salary.dto.CurrencyCohortAggregate;
import com.acme.salary.dto.RecentlyRaisedEmployee;
import com.acme.salary.dto.SalaryChangeOutcome;
import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.enums.RaiseType;
import com.acme.salary.repository.BulkRaiseRunRepository;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates bulk raises. Deliberately NOT @Transactional: each item runs in
 * its own REQUIRES_NEW transaction via BulkRaiseItemProcessor — partial
 * progress plus a review queue beats blocking a 10k cohort on one bad record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkRaiseService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final EmployeeRepository employeeRepository;
    private final SalaryChangeRepository salaryChangeRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final BulkRaiseRunRepository bulkRaiseRunRepository;
    private final BulkRaiseItemProcessor itemProcessor;
    private final BulkRaiseProperties properties;
    private final PaginationProperties paginationProperties;
    private final Clock clock;

    /**
     * Dry run: cost impact from per-currency SQL aggregates (10k rows are
     * never loaded), plus recently-raised employees for optional exclusion.
     */
    public BulkRaisePreviewResponse preview(BulkRaisePreviewRequest request) {
        List<CurrencyCohortAggregate> aggregates = employeeRepository
                .aggregateCohortByCurrency(blankToNull(request.filterCountry()),
                        blankToNull(request.filterDepartment()));
        Map<String, BigDecimal> usdRates = currencyRateRepository.findAll().stream()
                .collect(Collectors.toMap(CurrencyRate::getCode, CurrencyRate::getUsdRate));

        long affectedCount = 0;
        BigDecimal usdDelta = BigDecimal.ZERO;
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
        }

        List<RecentlyRaisedEmployee> recentlyRaised = salaryChangeRepository.findRecentlyRaised(
                blankToNull(request.filterCountry()), blankToNull(request.filterDepartment()),
                LocalDateTime.now(clock).minusDays(properties.recentlyRaisedDays()));

        return new BulkRaisePreviewResponse(affectedCount, costImpact, usdDelta, recentlyRaised);
    }

    public BulkRaiseRunResponse execute(BulkRaiseExecuteRequest request, String actor) {
        BulkRaiseRun run = bulkRaiseRunRepository.save(BulkRaiseRun.builder()
                .raiseType(request.raiseType())
                .raiseValue(request.value())
                .filterCountry(blankToNull(request.filterCountry()))
                .filterDepartment(blankToNull(request.filterDepartment()))
                .createdAt(LocalDateTime.now(clock))
                .build());

        List<Long> cohortIds = employeeRepository.findCohortIds(
                blankToNull(request.filterCountry()), blankToNull(request.filterDepartment()));
        Set<Long> excluded = new HashSet<>(request.excludedEmployeeIdsOrEmpty());
        ChangeType changeType = toChangeType(request.raiseType());

        int applied = 0;
        int parked = 0;
        int excludedCount = 0;
        for (Long employeeId : cohortIds) {
            if (excluded.contains(employeeId)) {
                excludedCount++;
                continue;
            }
            try {
                SalaryChangeOutcome outcome = itemProcessor.process(
                        employeeId, changeType, request.value(), run.getId(), actor);
                if (outcome.status() == SalaryChangeOutcome.Status.APPLIED) {
                    applied++;
                } else {
                    parked++;
                }
            } catch (RuntimeException e) {
                log.warn("Bulk raise run {} item failed for employee {}: {}",
                        run.getId(), employeeId, e.getMessage());
            }
        }

        run.setAppliedCount(applied);
        run.setReviewCount(parked);
        run.setExcludedCount(excludedCount);
        return BulkRaiseRunResponse.from(bulkRaiseRunRepository.save(run));
    }

    public PageResponse<BulkRaiseRunResponse> listRuns(int page, int size) {
        return PageResponse.from(bulkRaiseRunRepository.findAll(
                        PageRequest.of(
                                paginationProperties.clampPage(page),
                                paginationProperties.clampSize(size),
                                org.springframework.data.domain.Sort.by("createdAt").descending())),
                BulkRaiseRunResponse::from);
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

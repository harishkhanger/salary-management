package com.acme.salary.service;

import com.acme.salary.config.AnalyticsProperties;
import com.acme.salary.dto.response.AnalyticsResponses.CountrySpend;
import com.acme.salary.dto.response.AnalyticsResponses.DepartmentStats;
import com.acme.salary.dto.response.AnalyticsResponses.Distribution;
import com.acme.salary.dto.response.AnalyticsResponses.SalaryBucket;
import com.acme.salary.dto.response.AnalyticsResponses.Summary;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.repository.AnalyticsRepository;
import com.acme.salary.repository.AnalyticsRepository.DepartmentMedianRow;
import com.acme.salary.repository.AnalyticsRepository.SummaryRow;
import com.acme.salary.exception.ValidationException;
import com.acme.salary.repository.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Optional country/department filters apply to every endpoint so the
 * dashboard can slice the numbers; null/blank = whole org.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final AnalyticsProperties analyticsProperties;

    @Transactional(readOnly = true)
    public Summary summary(String country, String department) {
        SummaryRow row = analyticsRepository.summarize(blankToNull(country), blankToNull(department));
        Summary.LastPayrollRun lastRun = payrollRunRepository
                .findFirstByStatusOrderByCreatedAtDesc(JobStatus.COMPLETED)
                .map(run -> new Summary.LastPayrollRun(run.getYear(), run.getMonth(),
                        run.getProcessedCount(), run.getCreatedAt()))
                .orElse(null);
        return new Summary(row.getTotalMonthlySpendUsd(), row.getHeadcount(),
                row.getOnHoldCount(), lastRun);
    }

    @Transactional(readOnly = true)
    public List<CountrySpend> byCountry(String country, String department) {
        return analyticsRepository.spendByCountry(blankToNull(country), blankToNull(department))
                .stream()
                .map(row -> new CountrySpend(row.getCountry(), row.getHeadcount(),
                        row.getMonthlySpendUsd()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentStats> byDepartment(String country, String department) {
        Map<String, BigDecimal> medians = analyticsRepository
                .medianByDepartment(blankToNull(country), blankToNull(department)).stream()
                .collect(Collectors.toMap(DepartmentMedianRow::getDepartment,
                        DepartmentMedianRow::getMedianAnnualUsd));
        return analyticsRepository
                .averageByDepartment(blankToNull(country), blankToNull(department)).stream()
                .map(row -> new DepartmentStats(row.getDepartment(), row.getHeadcount(),
                        row.getAvgAnnualUsd(), medians.get(row.getDepartment())))
                .toList();
    }

    /** Band widths the dashboard may request; anything else is rejected. */
    private static final List<Integer> ALLOWED_BUCKETS = List.of(5_000, 10_000, 20_000, 50_000);

    @Transactional(readOnly = true)
    public Distribution salaryDistribution(String country, String department, Integer requestedBucketUsd) {
        int bucketUsd = requestedBucketUsd != null ? requestedBucketUsd
                : analyticsProperties.distributionBucketUsd();
        if (!ALLOWED_BUCKETS.contains(bucketUsd)) {
            throw new ValidationException("bucketUsd must be one of " + ALLOWED_BUCKETS);
        }
        List<SalaryBucket> buckets = analyticsRepository
                .salaryDistribution(bucketUsd, blankToNull(country), blankToNull(department)).stream()
                .map(row -> new SalaryBucket(row.getBucketFloorUsd(),
                        row.getBucketFloorUsd().add(BigDecimal.valueOf(bucketUsd)),
                        row.getEmployeeCount()))
                .toList();
        return new Distribution(bucketUsd, buckets);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

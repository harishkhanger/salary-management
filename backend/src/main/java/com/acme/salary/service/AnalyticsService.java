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
import com.acme.salary.repository.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final AnalyticsProperties analyticsProperties;

    @Transactional(readOnly = true)
    public Summary summary() {
        SummaryRow row = analyticsRepository.summarize();
        Summary.LastPayrollRun lastRun = payrollRunRepository
                .findFirstByStatusOrderByCreatedAtDesc(JobStatus.COMPLETED)
                .map(run -> new Summary.LastPayrollRun(run.getYear(), run.getMonth(),
                        run.getProcessedCount(), run.getCreatedAt()))
                .orElse(null);
        return new Summary(row.getTotalMonthlySpendUsd(), row.getHeadcount(),
                row.getOnHoldCount(), lastRun);
    }

    @Transactional(readOnly = true)
    public List<CountrySpend> byCountry() {
        return analyticsRepository.spendByCountry().stream()
                .map(row -> new CountrySpend(row.getCountry(), row.getHeadcount(),
                        row.getMonthlySpendUsd()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentStats> byDepartment() {
        Map<String, BigDecimal> medians = analyticsRepository.medianByDepartment().stream()
                .collect(Collectors.toMap(DepartmentMedianRow::getDepartment,
                        DepartmentMedianRow::getMedianAnnualUsd));
        return analyticsRepository.averageByDepartment().stream()
                .map(row -> new DepartmentStats(row.getDepartment(), row.getHeadcount(),
                        row.getAvgAnnualUsd(), medians.get(row.getDepartment())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Distribution salaryDistribution() {
        int bucketUsd = analyticsProperties.distributionBucketUsd();
        List<SalaryBucket> buckets = analyticsRepository.salaryDistribution(bucketUsd).stream()
                .map(row -> new SalaryBucket(row.getBucketFloorUsd(),
                        row.getBucketFloorUsd().add(BigDecimal.valueOf(bucketUsd)),
                        row.getEmployeeCount()))
                .toList();
        return new Distribution(bucketUsd, buckets);
    }
}

package com.acme.salary.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** The analytics dashboard payloads — every number is a SQL aggregate. */
public final class AnalyticsResponses {

    private AnalyticsResponses() {
    }

    public record Summary(
            BigDecimal totalMonthlySpendUsd,
            long headcount,
            long onHoldCount,
            LastPayrollRun lastPayrollRun
    ) {
        public record LastPayrollRun(int year, int month, int processedCount,
                                     LocalDateTime createdAt) {
        }
    }

    public record CountrySpend(String country, long headcount, BigDecimal monthlySpendUsd) {
    }

    public record DepartmentStats(String department, long headcount,
                                  BigDecimal avgAnnualUsd, BigDecimal medianAnnualUsd) {
    }

    /** Pay statistics for one group (a country or a department), annual USD. */
    public record PayStats(String label, long headcount, BigDecimal minUsd, BigDecimal maxUsd,
                           BigDecimal avgUsd, BigDecimal medianUsd) {
    }

    public record SalaryBucket(BigDecimal bucketFloorUsd, BigDecimal bucketCeilingUsd, long count) {
    }

    /**
     * minUsd/maxUsd are null for the whole-org histogram; when a custom range
     * was asked for they echo it and every band inside it is present (zero
     * counts included) so the chart is contiguous. total = employees in range.
     */
    public record Distribution(int bucketUsd, Integer minUsd, Integer maxUsd, long total,
                               List<SalaryBucket> buckets) {
    }
}

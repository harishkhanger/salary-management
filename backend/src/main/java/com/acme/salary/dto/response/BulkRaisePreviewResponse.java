package com.acme.salary.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record BulkRaisePreviewResponse(
        long affectedCount,
        List<CostImpactEntry> costImpact,
        BigDecimal costImpactUsdDelta,
        /** Annual payroll of the cohort in USD before and after the raise: current + delta = proposed. */
        BigDecimal costImpactUsdCurrent,
        BigDecimal costImpactUsdProposed,
        List<OverThresholdEmployee> overThreshold
) {
    public record CostImpactEntry(String currencyCode, BigDecimal current,
                                  BigDecimal proposed, BigDecimal delta) {
    }
}

package com.acme.salary.dto;

import java.math.BigDecimal;
import java.util.List;

public record BulkRaisePreviewResponse(
        long affectedCount,
        List<CostImpactEntry> costImpact,
        BigDecimal costImpactUsdDelta,
        List<RecentlyRaisedEmployee> recentlyRaised
) {
    public record CostImpactEntry(String currencyCode, BigDecimal current,
                                  BigDecimal proposed, BigDecimal delta) {
    }
}

package com.acme.salary.dto;

import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.enums.RaiseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BulkRaiseRunResponse(
        Long id,
        RaiseType raiseType,
        BigDecimal raiseValue,
        String filterCountry,
        String filterDepartment,
        int appliedCount,
        int reviewCount,
        int excludedCount,
        LocalDateTime createdAt
) {
    public static BulkRaiseRunResponse from(BulkRaiseRun run) {
        return new BulkRaiseRunResponse(run.getId(), run.getRaiseType(), run.getRaiseValue(),
                run.getFilterCountry(), run.getFilterDepartment(), run.getAppliedCount(),
                run.getReviewCount(), run.getExcludedCount(), run.getCreatedAt());
    }
}

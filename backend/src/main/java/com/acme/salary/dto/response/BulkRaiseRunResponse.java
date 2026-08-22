package com.acme.salary.dto.response;

import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.enums.RaiseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BulkRaiseRunResponse(
        Long id,
        RaiseType raiseType,
        BigDecimal raiseValue,
        String filterCountry,
        String filterDepartment,
        JobStatus status,
        int appliedCount,
        int reviewCount,
        int excludedCount,
        /** Size of the hand-picked cohort; 0 when the run used filters. */
        int selectedCount,
        String initiatedBy,
        LocalDateTime createdAt
) {
    public static BulkRaiseRunResponse from(BulkRaiseRun run) {
        return new BulkRaiseRunResponse(run.getId(), run.getRaiseType(), run.getRaiseValue(),
                run.getFilterCountry(), run.getFilterDepartment(), run.getStatus(),
                run.getAppliedCount(), run.getReviewCount(), run.getExcludedCount(),
                countIds(run.getEmployeeIds()), run.getInitiatedBy(), run.getCreatedAt());
    }

    /** Cheap count of a JSON id array without a parser dependency here. */
    private static int countIds(String json) {
        if (json == null || json.isBlank() || json.trim().equals("[]")) {
            return 0;
        }
        return json.split(",").length;
    }
}

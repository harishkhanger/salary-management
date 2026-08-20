package com.acme.salary.dto.response;

import com.acme.salary.entities.PayrollRun;
import com.acme.salary.enums.JobStatus;

import java.time.LocalDateTime;

public record PayrollRunResponse(
        Long id,
        int year,
        int month,
        JobStatus status,
        Long employeeId,
        int processedCount,
        int skippedHeldCount,
        int alreadyProcessedCount,
        String initiatedBy,
        LocalDateTime createdAt
) {
    public static PayrollRunResponse from(PayrollRun run) {
        return new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(),
                run.getStatus(), run.getEmployeeId(), run.getProcessedCount(),
                run.getSkippedHeldCount(), run.getAlreadyProcessedCount(),
                run.getInitiatedBy(), run.getCreatedAt());
    }
}

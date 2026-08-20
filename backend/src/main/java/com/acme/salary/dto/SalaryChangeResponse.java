package com.acme.salary.dto;

import com.acme.salary.entities.SalaryChange;
import com.acme.salary.enums.ChangeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalaryChangeResponse(
        Long id,
        Long employeeId,
        BigDecimal oldSalary,
        BigDecimal newSalary,
        ChangeType changeType,
        BigDecimal percentValue,
        String actor,
        Long bulkRaiseRunId,
        LocalDateTime createdAt
) {
    public static SalaryChangeResponse from(SalaryChange c) {
        return new SalaryChangeResponse(c.getId(), c.getEmployeeId(), c.getOldSalary(),
                c.getNewSalary(), c.getChangeType(), c.getPercentValue(), c.getActor(),
                c.getBulkRaiseRunId(), c.getCreatedAt());
    }
}

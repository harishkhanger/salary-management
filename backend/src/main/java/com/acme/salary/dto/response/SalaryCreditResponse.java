package com.acme.salary.dto.response;

import com.acme.salary.entities.SalaryCredit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalaryCreditResponse(
        Long id,
        Long employeeId,
        int year,
        int month,
        BigDecimal amount,
        String currencyCode,
        BigDecimal usdRate,
        Long payrollRunId,
        LocalDateTime createdAt
) {
    public static SalaryCreditResponse from(SalaryCredit c) {
        return new SalaryCreditResponse(c.getId(), c.getEmployeeId(), c.getYear(), c.getMonth(),
                c.getAmount(), c.getCurrencyCode(), c.getUsdRate(), c.getPayrollRunId(),
                c.getCreatedAt());
    }
}

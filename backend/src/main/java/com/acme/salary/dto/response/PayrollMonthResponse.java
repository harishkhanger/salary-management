package com.acme.salary.dto.response;

import com.acme.salary.enums.PayrollMonthState;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the month-centric payroll screen. unpaidCount is the number of
 * ACTIVE employees without a credit for the period — what "Pay" would credit.
 */
public record PayrollMonthResponse(
        int year,
        int month,
        PayrollMonthState state,
        long creditedCount,
        long unpaidCount,
        long heldCount,
        LocalDateTime lastPaidAt,
        LocalDate opensOn,
        Long activeRunId
) {
}

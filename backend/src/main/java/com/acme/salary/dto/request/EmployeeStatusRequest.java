package com.acme.salary.dto.request;

import com.acme.salary.enums.EmployeeStatus;
import jakarta.validation.constraints.NotNull;

/** Salary hold / release. Holds block payout, never compensation changes. */
public record EmployeeStatusRequest(
        @NotNull EmployeeStatus status,
        @NotNull Integer version
) {
}

package com.acme.salary.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** employeeId null = process the whole org. */
public record PayrollRunRequest(
        @NotNull @Min(2000) @Max(2100)
        Integer year,

        @NotNull @Min(1) @Max(12)
        Integer month,

        Long employeeId
) {
}

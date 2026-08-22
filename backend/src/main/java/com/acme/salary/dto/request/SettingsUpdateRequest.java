package com.acme.salary.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/** Partial update: send only the settings you want to change; omitted fields keep their value. */
public record SettingsUpdateRequest(
        @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
        BigDecimal raiseThresholdPercent,

        @Min(1) @Max(28)
        Integer payrollDay
) {
}

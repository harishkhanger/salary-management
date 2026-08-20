package com.acme.salary.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SettingsUpdateRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
        BigDecimal raiseThresholdPercent
) {
}

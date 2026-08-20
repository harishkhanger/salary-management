package com.acme.salary.dto.request;

import com.acme.salary.enums.ChangeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * value semantics per type: PERCENT = percent points (10 = +10%),
 * AMOUNT = raise delta, CORRECTION = the absolute new salary.
 */
public record SalaryChangeRequest(
        @NotNull
        ChangeType changeType,

        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal value,

        @NotNull
        Integer version
) {
}

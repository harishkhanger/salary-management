package com.acme.salary.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** New local-per-USD rate. Affects future credits/analytics only — never history. */
public record CurrencyRateUpdateRequest(
        @NotNull @DecimalMin("0.000001") @Digits(integer = 6, fraction = 6)
        BigDecimal usdRate
) {
}

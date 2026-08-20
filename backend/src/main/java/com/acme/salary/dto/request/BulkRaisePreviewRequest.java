package com.acme.salary.dto.request;

import com.acme.salary.enums.RaiseType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** No filters = the whole org. */
public record BulkRaisePreviewRequest(
        @NotNull
        RaiseType raiseType,

        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal value,

        String filterCountry,
        String filterDepartment
) {
}

package com.acme.salary.dto.request;

import com.acme.salary.enums.RaiseType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** No filters and no employeeIds = the whole org; employeeIds = exactly those people (filters ignored). */
public record BulkRaisePreviewRequest(
        @NotNull
        RaiseType raiseType,

        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal value,

        String filterCountry,
        String filterDepartment,
        List<Long> employeeIds
) {
    public List<Long> employeeIdsOrEmpty() {
        return employeeIds == null ? List.of() : employeeIds;
    }
}

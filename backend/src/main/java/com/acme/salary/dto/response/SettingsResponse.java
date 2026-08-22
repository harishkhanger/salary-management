package com.acme.salary.dto.response;

import com.acme.salary.entities.OrgSettings;

import java.math.BigDecimal;

public record SettingsResponse(BigDecimal raiseThresholdPercent, int payrollDay) {

    public static SettingsResponse from(OrgSettings settings) {
        return new SettingsResponse(settings.getRaiseThresholdPercent(), settings.getPayrollDay());
    }
}

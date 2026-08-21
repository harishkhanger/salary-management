package com.acme.salary.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Flagged in the bulk-raise preview so HR can optionally exclude them: raises
 * since the first recorded change total more than the guardrail threshold.
 */
public record OverThresholdEmployee(Long employeeId, String employeeCode, String name,
                                    BigDecimal totalRaisePercent, LocalDateTime lastRaiseAt) {
}

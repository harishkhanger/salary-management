package com.acme.salary.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection for the preview flag: current salary against the salary before
 * the employee's first recorded change. The percentage is derived in the service.
 */
public record OverThresholdAggregate(Long employeeId, String employeeCode, String name,
                                     BigDecimal currentSalary, BigDecimal baselineSalary,
                                     LocalDateTime lastRaiseAt) {
}

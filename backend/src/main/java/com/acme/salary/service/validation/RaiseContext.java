package com.acme.salary.service.validation;

import com.acme.salary.entities.Employee;
import com.acme.salary.enums.ChangeType;

import java.math.BigDecimal;

public record RaiseContext(
        Employee employee,
        BigDecimal currentSalary,
        BigDecimal proposedSalary,
        ChangeType changeType,
        BigDecimal percentValue
) {
}

package com.acme.salary.service.strategy;

import com.acme.salary.enums.ChangeType;

import java.math.BigDecimal;

/**
 * Strategy: how a salary change value is turned into the new salary.
 * One implementation per ChangeType; SalaryChangeService selects by type.
 */
public interface RaiseCalculation {

    ChangeType type();

    BigDecimal newSalary(BigDecimal currentSalary, BigDecimal value);
}

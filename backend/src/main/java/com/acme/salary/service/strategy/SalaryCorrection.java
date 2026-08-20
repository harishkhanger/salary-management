package com.acme.salary.service.strategy;

import com.acme.salary.enums.ChangeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Correction sets the salary to an absolute value (upward or downward);
 * the append-only history records it as one more change, never an edit.
 */
@Component
public class SalaryCorrection implements RaiseCalculation {

    @Override
    public ChangeType type() {
        return ChangeType.CORRECTION;
    }

    @Override
    public BigDecimal newSalary(BigDecimal currentSalary, BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

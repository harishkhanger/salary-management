package com.acme.salary.service.strategy;

import com.acme.salary.enums.ChangeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PercentageRaise implements RaiseCalculation {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public ChangeType type() {
        return ChangeType.PERCENT;
    }

    @Override
    public BigDecimal newSalary(BigDecimal currentSalary, BigDecimal value) {
        BigDecimal raise = currentSalary.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return currentSalary.add(raise);
    }
}

package com.acme.salary.service.strategy;

import com.acme.salary.enums.ChangeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FlatAmountRaise implements RaiseCalculation {

    @Override
    public ChangeType type() {
        return ChangeType.AMOUNT;
    }

    @Override
    public BigDecimal newSalary(BigDecimal currentSalary, BigDecimal value) {
        return currentSalary.add(value.setScale(2, RoundingMode.HALF_UP));
    }
}

package com.acme.salary.service.strategy;


import com.acme.salary.enums.ChangeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RaiseCalculationTest {

    private final PercentageRaise percentageRaise = new PercentageRaise();
    private final FlatAmountRaise flatAmountRaise = new FlatAmountRaise();
    private final SalaryCorrection salaryCorrection = new SalaryCorrection();

    @Test
    void percentageRaiseAddsPercentOfCurrentSalary() {
        assertThat(percentageRaise.newSalary(new BigDecimal("1000000.00"), new BigDecimal("10")))
                .isEqualByComparingTo("1100000.00");
    }

    @Test
    void percentageRaiseRoundsHalfUpToTwoDecimals() {
        // 3.33% of 99999.99 = 3329.9996... -> 103329.99 + rounding
        assertThat(percentageRaise.newSalary(new BigDecimal("99999.99"), new BigDecimal("3.33")))
                .isEqualByComparingTo("103329.99");
    }

    @Test
    void flatAmountRaiseAddsDelta() {
        assertThat(flatAmountRaise.newSalary(new BigDecimal("90000.00"), new BigDecimal("5000")))
                .isEqualByComparingTo("95000.00");
    }

    @Test
    void correctionSetsAbsoluteValueIgnoringCurrent() {
        assertThat(salaryCorrection.newSalary(new BigDecimal("90000.00"), new BigDecimal("82000")))
                .isEqualByComparingTo("82000.00");
    }

    @Test
    void strategiesDeclareTheirChangeType() {
        assertThat(percentageRaise.type()).isEqualTo(ChangeType.PERCENT);
        assertThat(flatAmountRaise.type()).isEqualTo(ChangeType.AMOUNT);
        assertThat(salaryCorrection.type()).isEqualTo(ChangeType.CORRECTION);
    }
}

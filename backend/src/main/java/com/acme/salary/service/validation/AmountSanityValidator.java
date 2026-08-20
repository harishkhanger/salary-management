package com.acme.salary.service.validation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parks any single change that more than doubles a salary — almost always a
 * fat-finger (10 vs 100, missing decimal). Parking beats rejecting: in bulk
 * runs the rest of the batch proceeds and a human decides on the outlier.
 */
@Component
@Order(1)
public class AmountSanityValidator implements RaiseValidator {

    private static final BigDecimal TWO = new BigDecimal("2");

    @Override
    public Optional<String> validate(RaiseContext context) {
        if (context.proposedSalary().compareTo(context.currentSalary().multiply(TWO)) > 0) {
            return Optional.of("Single change exceeds 100% of current salary ("
                    + context.currentSalary() + " -> " + context.proposedSalary() + ")");
        }
        return Optional.empty();
    }
}

package com.acme.salary.service.validation;

import com.acme.salary.entities.SalaryChange;
import com.acme.salary.repository.OrgSettingsRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The guardrail: cumulative raise over the trailing 12 months may not exceed
 * the configurable OrgSettings threshold. Baseline = the salary at the start
 * of the window (old_salary of the earliest change inside it; current salary
 * when there were no changes). Cumulative % = (proposed - baseline) / baseline.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class CumulativeThresholdValidator implements RaiseValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SalaryChangeRepository salaryChangeRepository;
    private final OrgSettingsRepository orgSettingsRepository;
    private final Clock clock;

    @Override
    public Optional<String> validate(RaiseContext context) {
        // a change that does not increase the salary cannot push the
        // cumulative raise above the threshold — downward corrections pass
        // even when the trailing window is already above it
        if (context.proposedSalary().compareTo(context.currentSalary()) <= 0) {
            return Optional.empty();
        }
        BigDecimal threshold = orgSettingsRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("org_settings row missing"))
                .getRaiseThresholdPercent();

        LocalDateTime windowStart = LocalDateTime.now(clock).minusMonths(12);
        BigDecimal baseline = salaryChangeRepository
                .findEarliestChangeInWindow(context.employee().getId(), windowStart)
                .map(SalaryChange::getOldSalary)
                .orElse(context.currentSalary());

        BigDecimal cumulativePercent = context.proposedSalary().subtract(baseline)
                .multiply(HUNDRED)
                .divide(baseline, 2, RoundingMode.HALF_UP);

        if (cumulativePercent.compareTo(threshold) > 0) {
            return Optional.of("Cumulative 12-month raise " + cumulativePercent
                    + "% exceeds threshold " + threshold + "%");
        }
        return Optional.empty();
    }
}

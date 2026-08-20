package com.acme.salary.service;

import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.SalaryCredit;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.SalaryCreditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * One credit = one transaction (per Harish: money gets the smallest possible
 * blast radius per commit). Separate bean for the same proxy-semantics reason
 * as BulkRaiseItemProcessor. The credit snapshots amount, currency, and the
 * USD rate AT CREDIT TIME — rate edits never touch history.
 */
@Component
@RequiredArgsConstructor
public class PayrollItemProcessor {

    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    private final EmployeeRepository employeeRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final SalaryCreditRepository salaryCreditRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SalaryCredit credit(Long employeeId, int year, int month, Long runId) {
        Employee employee = employeeRepository.findByIdAndDeletedFalse(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + employeeId));
        CurrencyRate rate = currencyRateRepository.findById(employee.getCurrencyCode())
                .orElseThrow(() -> new NotFoundException(
                        "No rate for currency: " + employee.getCurrencyCode()));

        BigDecimal monthlyAmount = employee.getAnnualSalary()
                .divide(MONTHS_PER_YEAR, 2, RoundingMode.HALF_UP);
        return salaryCreditRepository.save(SalaryCredit.builder()
                .employeeId(employeeId)
                .year(year)
                .month(month)
                .amount(monthlyAmount)
                .currencyCode(employee.getCurrencyCode())
                .usdRate(rate.getUsdRate())
                .payrollRunId(runId)
                .createdAt(LocalDateTime.now(clock))
                .build());
    }
}

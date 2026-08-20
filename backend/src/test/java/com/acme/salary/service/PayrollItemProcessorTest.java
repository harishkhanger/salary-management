package com.acme.salary.service;

import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.SalaryCredit;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.SalaryCreditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollItemProcessorTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @Mock
    private SalaryCreditRepository salaryCreditRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PayrollItemProcessor processor() {
        return new PayrollItemProcessor(employeeRepository, currencyRateRepository,
                salaryCreditRepository, eventPublisher, FIXED);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(7L);
        e.setAnnualSalary(new BigDecimal("1200000.00"));
        e.setCurrencyCode("INR");
        return e;
    }

    @Test
    void creditSnapshotsMonthlyAmountCurrencyAndRateAtCreditTime() {
        CurrencyRate inr = new CurrencyRate();
        inr.setCode("INR");
        inr.setUsdRate(new BigDecimal("83.500000"));
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee()));
        when(currencyRateRepository.findById("INR")).thenReturn(Optional.of(inr));
        when(salaryCreditRepository.save(any(SalaryCredit.class))).thenAnswer(inv -> inv.getArgument(0));

        processor().credit(7L, 2026, 7, 11L);

        ArgumentCaptor<SalaryCredit> credit = ArgumentCaptor.forClass(SalaryCredit.class);
        verify(salaryCreditRepository).save(credit.capture());
        assertThat(credit.getValue().getAmount()).isEqualByComparingTo("100000.00");
        assertThat(credit.getValue().getCurrencyCode()).isEqualTo("INR");
        assertThat(credit.getValue().getUsdRate()).isEqualByComparingTo("83.500000");
        assertThat(credit.getValue().getPayrollRunId()).isEqualTo(11L);
        assertThat(credit.getValue().getYear()).isEqualTo(2026);
        assertThat(credit.getValue().getMonth()).isEqualTo(7);
    }

    @Test
    void monthlyAmountRoundsHalfUp() {
        Employee e = employee();
        e.setAnnualSalary(new BigDecimal("100000.00")); // /12 = 8333.333... -> 8333.33
        CurrencyRate inr = new CurrencyRate();
        inr.setCode("INR");
        inr.setUsdRate(new BigDecimal("83.500000"));
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(e));
        when(currencyRateRepository.findById("INR")).thenReturn(Optional.of(inr));
        when(salaryCreditRepository.save(any(SalaryCredit.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<SalaryCredit> credit = ArgumentCaptor.forClass(SalaryCredit.class);
        processor().credit(7L, 2026, 7, 11L);
        verify(salaryCreditRepository).save(credit.capture());

        assertThat(credit.getValue().getAmount()).isEqualByComparingTo("8333.33");
    }

    @Test
    void missingCurrencyRateFailsTheItem() {
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee()));
        when(currencyRateRepository.findById("INR")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor().credit(7L, 2026, 7, 11L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("INR");
        verify(salaryCreditRepository, never()).save(any());
    }
}

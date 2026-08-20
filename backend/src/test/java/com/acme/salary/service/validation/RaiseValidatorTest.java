package com.acme.salary.service.validation;

import com.acme.salary.entities.Employee;
import com.acme.salary.entities.OrgSettings;
import com.acme.salary.entities.SalaryChange;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.repository.OrgSettingsRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaiseValidatorTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SalaryChangeRepository salaryChangeRepository;

    @Mock
    private OrgSettingsRepository orgSettingsRepository;

    private Employee employee(String salary) {
        Employee e = new Employee();
        e.setId(7L);
        e.setAnnualSalary(new BigDecimal(salary));
        return e;
    }

    private RaiseContext context(String current, String proposed) {
        return new RaiseContext(employee(current), new BigDecimal(current),
                new BigDecimal(proposed), ChangeType.PERCENT, null);
    }

    // --- amount sanity ---

    @Test
    void sanityPassesForOrdinaryRaise() {
        assertThat(new AmountSanityValidator().validate(context("100000.00", "120000.00"))).isEmpty();
    }

    @Test
    void sanityParksSingleChangeAboveHundredPercent() {
        Optional<String> reason = new AmountSanityValidator().validate(context("100000.00", "200000.01"));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("100%");
    }

    // --- cumulative threshold ---

    private CumulativeThresholdValidator thresholdValidator() {
        return new CumulativeThresholdValidator(salaryChangeRepository, orgSettingsRepository, FIXED);
    }

    private void stubThreshold(String percent) {
        OrgSettings settings = new OrgSettings();
        settings.setId(1L);
        settings.setRaiseThresholdPercent(new BigDecimal(percent));
        when(orgSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
    }

    private SalaryChange earliestChange(String oldSalary) {
        SalaryChange change = new SalaryChange();
        change.setOldSalary(new BigDecimal(oldSalary));
        change.setCreatedAt(LocalDateTime.now(FIXED).minusMonths(6));
        return change;
    }

    @Test
    void underThresholdWithNoHistoryPasses() {
        stubThreshold("30.00");
        when(salaryChangeRepository.findEarliestChangeInWindow(eq(7L), any())).thenReturn(Optional.empty());

        // 20% over current baseline
        assertThat(thresholdValidator().validate(context("100000.00", "120000.00"))).isEmpty();
    }

    @Test
    void overThresholdAgainstTwelveMonthBaselineParks() {
        stubThreshold("30.00");
        // salary was 100k at window start; already raised to 115k; +20k more = 35% cumulative
        when(salaryChangeRepository.findEarliestChangeInWindow(eq(7L), any())).thenReturn(Optional.of(earliestChange("100000.00")));

        Optional<String> reason = thresholdValidator().validate(context("115000.00", "135000.00"));

        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("35.00%").contains("30");
    }

    @Test
    void exactlyAtThresholdPasses() {
        stubThreshold("30.00");
        when(salaryChangeRepository.findEarliestChangeInWindow(eq(7L), any())).thenReturn(Optional.of(earliestChange("100000.00")));

        assertThat(thresholdValidator().validate(context("115000.00", "130000.00"))).isEmpty();
    }

    @Test
    void downwardCorrectionPasses() {
        stubThreshold("30.00");
        when(salaryChangeRepository.findEarliestChangeInWindow(eq(7L), any())).thenReturn(Optional.empty());

        assertThat(thresholdValidator().validate(context("100000.00", "80000.00"))).isEmpty();
    }
}

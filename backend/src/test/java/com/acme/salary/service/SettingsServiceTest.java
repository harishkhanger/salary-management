package com.acme.salary.service;

import com.acme.salary.dto.request.CurrencyRateUpdateRequest;
import com.acme.salary.dto.request.SettingsUpdateRequest;
import com.acme.salary.dto.response.CurrencyRateResponse;
import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.OrgSettings;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.OrgSettingsRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @Mock
    private OrgSettingsRepository orgSettingsRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private SettingsService service() {
        return new SettingsService(currencyRateRepository, orgSettingsRepository, eventPublisher, FIXED);
    }

    @Test
    void updateRateSetsValueAndTimestamp() {
        CurrencyRate inr = new CurrencyRate();
        inr.setCode("INR");
        inr.setName("Indian Rupee");
        inr.setUsdRate(new BigDecimal("83.500000"));
        inr.setUpdatedAt(LocalDateTime.now(FIXED).minusDays(30));
        when(currencyRateRepository.findById("INR")).thenReturn(Optional.of(inr));
        when(currencyRateRepository.save(any(CurrencyRate.class))).thenAnswer(inv -> inv.getArgument(0));

        CurrencyRateResponse response = service().updateRate("INR",
                new CurrencyRateUpdateRequest(new BigDecimal("84.250000")));

        assertThat(response.usdRate()).isEqualByComparingTo("84.250000");
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.now(FIXED));
    }

    @Test
    void updateRateRejectsUnknownCurrency() {
        when(currencyRateRepository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateRate("XXX",
                new CurrencyRateUpdateRequest(new BigDecimal("1"))))
                .isInstanceOf(NotFoundException.class);
        verify(currencyRateRepository, never()).save(any());
    }

    @Test
    void thresholdRoundTripsThroughTheSingleSettingsRow() {
        OrgSettings settings = new OrgSettings();
        settings.setId(1L);
        settings.setRaiseThresholdPercent(new BigDecimal("30.00"));
        when(orgSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(orgSettingsRepository.save(any(OrgSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service().getSettings().raiseThresholdPercent()).isEqualByComparingTo("30.00");

        var updated = service().updateSettings(new SettingsUpdateRequest(new BigDecimal("25.00"), null));
        assertThat(updated.raiseThresholdPercent()).isEqualByComparingTo("25.00");
    }

    @Test
    void updatePayrollDayOnlyLeavesThresholdUntouched() {
        OrgSettings row = new OrgSettings();
        row.setId(1L);
        row.setRaiseThresholdPercent(new BigDecimal("30.00"));
        row.setPayrollDay(25);
        when(orgSettingsRepository.findById(1L)).thenReturn(java.util.Optional.of(row));
        when(orgSettingsRepository.save(any(OrgSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = service().updateSettings(new SettingsUpdateRequest(null, 20));

        assertThat(updated.payrollDay()).isEqualTo(20);
        assertThat(updated.raiseThresholdPercent()).isEqualByComparingTo("30.00");
    }
}

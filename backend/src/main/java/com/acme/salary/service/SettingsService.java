package com.acme.salary.service;

import com.acme.salary.dto.request.CurrencyRateUpdateRequest;
import com.acme.salary.dto.request.SettingsUpdateRequest;
import com.acme.salary.dto.response.CurrencyRateResponse;
import com.acme.salary.dto.response.SettingsResponse;
import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.OrgSettings;
import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import com.acme.salary.events.AuditEvent;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.OrgSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The manually managed settings surface: ~10 currency rates and the guardrail
 * threshold. Rate edits affect future credits/analytics only — history keeps
 * its snapshots. The threshold takes effect on the next validated change (the
 * validator reads it per call).
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final long SETTINGS_ROW_ID = 1L;

    private final CurrencyRateRepository currencyRateRepository;
    private final OrgSettingsRepository orgSettingsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CurrencyRateResponse> listCurrencies() {
        return currencyRateRepository.findAll(Sort.by("code").ascending()).stream()
                .map(CurrencyRateResponse::from)
                .toList();
    }

    @Transactional
    public CurrencyRateResponse updateRate(String code, CurrencyRateUpdateRequest request) {
        CurrencyRate rate = currencyRateRepository.findById(code)
                .orElseThrow(() -> new NotFoundException("Unknown currency: " + code));
        java.math.BigDecimal oldRate = rate.getUsdRate();
        rate.setUsdRate(request.usdRate());
        rate.setUpdatedAt(LocalDateTime.now(clock));
        CurrencyRate saved = currencyRateRepository.save(rate);
        // currencies have no numeric id: entityId 0 by convention, code in changed_fields
        eventPublisher.publishEvent(AuditEvent.builder()
                .entityType(AuditEntityType.CURRENCY).entityId(0L)
                .action(AuditAction.RATE_UPDATED).actor(currentActor())
                .changedFields(java.util.Map.of("code", code, "usdRate",
                        java.util.Map.of("old", oldRate.toPlainString(),
                                "new", request.usdRate().toPlainString())))
                .build());
        return CurrencyRateResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return SettingsResponse.from(settingsRow());
    }

    /** Partial update; each changed setting gets its own audit row. */
    @Transactional
    public SettingsResponse updateSettings(SettingsUpdateRequest request) {
        if (request.raiseThresholdPercent() == null && request.payrollDay() == null) {
            throw new com.acme.salary.exception.ValidationException("Nothing to update — send a threshold or a payroll day");
        }
        OrgSettings settings = settingsRow();
        if (request.raiseThresholdPercent() != null) {
            java.math.BigDecimal oldThreshold = settings.getRaiseThresholdPercent();
            settings.setRaiseThresholdPercent(request.raiseThresholdPercent());
            eventPublisher.publishEvent(AuditEvent.builder()
                    .entityType(AuditEntityType.SETTINGS).entityId(SETTINGS_ROW_ID)
                    .action(AuditAction.THRESHOLD_UPDATED).actor(currentActor())
                    .changedFields(java.util.Map.of("raiseThresholdPercent",
                            java.util.Map.of("old", oldThreshold.toPlainString(),
                                    "new", request.raiseThresholdPercent().toPlainString())))
                    .build());
        }
        if (request.payrollDay() != null) {
            int oldDay = settings.getPayrollDay();
            settings.setPayrollDay(request.payrollDay());
            eventPublisher.publishEvent(AuditEvent.builder()
                    .entityType(AuditEntityType.SETTINGS).entityId(SETTINGS_ROW_ID)
                    .action(AuditAction.PAYROLL_DAY_UPDATED).actor(currentActor())
                    .changedFields(java.util.Map.of("payrollDay",
                            java.util.Map.of("old", String.valueOf(oldDay),
                                    "new", String.valueOf(request.payrollDay()))))
                    .build());
        }
        return SettingsResponse.from(orgSettingsRepository.save(settings));
    }

    private String currentActor() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private OrgSettings settingsRow() {
        return orgSettingsRepository.findById(SETTINGS_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("org_settings row missing"));
    }
}

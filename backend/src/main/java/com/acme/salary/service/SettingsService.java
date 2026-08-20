package com.acme.salary.service;

import com.acme.salary.dto.request.CurrencyRateUpdateRequest;
import com.acme.salary.dto.request.SettingsUpdateRequest;
import com.acme.salary.dto.response.CurrencyRateResponse;
import com.acme.salary.dto.response.SettingsResponse;
import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.OrgSettings;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.OrgSettingsRepository;
import lombok.RequiredArgsConstructor;
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
        rate.setUsdRate(request.usdRate());
        rate.setUpdatedAt(LocalDateTime.now(clock));
        return CurrencyRateResponse.from(currencyRateRepository.save(rate));
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return SettingsResponse.from(settingsRow());
    }

    @Transactional
    public SettingsResponse updateSettings(SettingsUpdateRequest request) {
        OrgSettings settings = settingsRow();
        settings.setRaiseThresholdPercent(request.raiseThresholdPercent());
        return SettingsResponse.from(orgSettingsRepository.save(settings));
    }

    private OrgSettings settingsRow() {
        return orgSettingsRepository.findById(SETTINGS_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("org_settings row missing"));
    }
}

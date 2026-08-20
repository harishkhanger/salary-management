package com.acme.salary.controller;

import com.acme.salary.dto.request.CurrencyRateUpdateRequest;
import com.acme.salary.dto.response.CurrencyRateResponse;
import com.acme.salary.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final SettingsService settingsService;

    @GetMapping
    public List<CurrencyRateResponse> list() {
        return settingsService.listCurrencies();
    }

    @PutMapping("/{code}")
    public CurrencyRateResponse updateRate(@PathVariable String code,
                                           @Valid @RequestBody CurrencyRateUpdateRequest request) {
        return settingsService.updateRate(code, request);
    }
}

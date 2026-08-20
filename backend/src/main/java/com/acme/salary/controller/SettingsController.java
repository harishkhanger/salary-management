package com.acme.salary.controller;

import com.acme.salary.dto.request.SettingsUpdateRequest;
import com.acme.salary.dto.response.SettingsResponse;
import com.acme.salary.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public SettingsResponse get() {
        return settingsService.getSettings();
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsUpdateRequest request) {
        return settingsService.updateSettings(request);
    }
}

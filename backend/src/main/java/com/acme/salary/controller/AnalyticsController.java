package com.acme.salary.controller;

import com.acme.salary.dto.response.AnalyticsResponses.CountrySpend;
import com.acme.salary.dto.response.AnalyticsResponses.DepartmentStats;
import com.acme.salary.dto.response.AnalyticsResponses.Distribution;
import com.acme.salary.dto.response.AnalyticsResponses.Summary;
import com.acme.salary.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public Summary summary() {
        return analyticsService.summary();
    }

    @GetMapping("/by-country")
    public List<CountrySpend> byCountry() {
        return analyticsService.byCountry();
    }

    @GetMapping("/by-department")
    public List<DepartmentStats> byDepartment() {
        return analyticsService.byDepartment();
    }

    @GetMapping("/salary-distribution")
    public Distribution salaryDistribution() {
        return analyticsService.salaryDistribution();
    }
}

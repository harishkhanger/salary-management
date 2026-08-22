package com.acme.salary.controller;

import com.acme.salary.dto.response.AnalyticsResponses.CountrySpend;
import com.acme.salary.dto.response.AnalyticsResponses.DepartmentStats;
import com.acme.salary.dto.response.AnalyticsResponses.PayStats;
import com.acme.salary.dto.response.AnalyticsResponses.Distribution;
import com.acme.salary.dto.response.AnalyticsResponses.Summary;
import com.acme.salary.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public Summary summary(@RequestParam(required = false) String country,
                           @RequestParam(required = false) String department) {
        return analyticsService.summary(country, department);
    }

    @GetMapping("/by-country")
    public List<CountrySpend> byCountry(@RequestParam(required = false) String country,
                                        @RequestParam(required = false) String department) {
        return analyticsService.byCountry(country, department);
    }

    @GetMapping("/by-department")
    public List<DepartmentStats> byDepartment(@RequestParam(required = false) String country,
                                              @RequestParam(required = false) String department) {
        return analyticsService.byDepartment(country, department);
    }

    /** ?groupBy=country|department&countries=India,Germany&department=Engineering */
    @GetMapping("/pay-stats")
    public List<PayStats> payStats(@RequestParam(defaultValue = "country") String groupBy,
                                   @RequestParam(required = false) List<String> countries,
                                   @RequestParam(required = false) String department) {
        return analyticsService.payStats(groupBy, countries, department);
    }

    @GetMapping("/salary-distribution")
    public Distribution salaryDistribution(@RequestParam(required = false) String country,
                                           @RequestParam(required = false) String department,
                                           @RequestParam(required = false) Integer bucketUsd,
                                           @RequestParam(required = false) Integer minUsd,
                                           @RequestParam(required = false) Integer maxUsd) {
        return analyticsService.salaryDistribution(country, department, bucketUsd, minUsd, maxUsd);
    }
}

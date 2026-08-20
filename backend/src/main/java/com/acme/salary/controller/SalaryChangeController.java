package com.acme.salary.controller;

import com.acme.salary.dto.PageResponse;
import com.acme.salary.dto.SalaryChangeOutcome;
import com.acme.salary.dto.SalaryChangeRequest;
import com.acme.salary.dto.SalaryChangeResponse;
import com.acme.salary.service.SalaryChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees/{employeeId}/salary-changes")
@RequiredArgsConstructor
public class SalaryChangeController {

    // @TODO placeholder until the auth increment supplies the session user
    private static final String ACTOR = "hr";

    private final SalaryChangeService salaryChangeService;

    @PostMapping
    public SalaryChangeOutcome apply(@PathVariable Long employeeId,
                                     @Valid @RequestBody SalaryChangeRequest request) {
        return salaryChangeService.apply(employeeId, request, ACTOR);
    }

    @GetMapping
    public PageResponse<SalaryChangeResponse> history(@PathVariable Long employeeId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return salaryChangeService.history(employeeId, page, size);
    }
}

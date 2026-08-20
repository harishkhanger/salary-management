package com.acme.salary.controller;

import java.security.Principal;

import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.response.SalaryChangeOutcome;
import com.acme.salary.dto.request.SalaryChangeRequest;
import com.acme.salary.dto.response.SalaryChangeResponse;
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

    private final SalaryChangeService salaryChangeService;

    @PostMapping
    public SalaryChangeOutcome apply(@PathVariable Long employeeId,
                                     @Valid @RequestBody SalaryChangeRequest request,
                                     Principal principal) {
        return salaryChangeService.apply(employeeId, request, principal.getName());
    }

    @GetMapping
    public PageResponse<SalaryChangeResponse> history(@PathVariable Long employeeId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return salaryChangeService.history(employeeId, page, size);
    }
}

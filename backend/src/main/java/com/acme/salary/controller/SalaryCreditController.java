package com.acme.salary.controller;

import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.response.SalaryCreditResponse;
import com.acme.salary.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees/{employeeId}/credits")
@RequiredArgsConstructor
public class SalaryCreditController {

    private final PayrollService payrollService;

    @GetMapping
    public PageResponse<SalaryCreditResponse> history(@PathVariable Long employeeId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return payrollService.creditHistory(employeeId, page, size);
    }
}

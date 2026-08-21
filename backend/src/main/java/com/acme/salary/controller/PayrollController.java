package com.acme.salary.controller;

import java.security.Principal;
import java.util.List;

import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.request.PayrollRunRequest;
import com.acme.salary.dto.response.PayrollMonthResponse;
import com.acme.salary.dto.response.PayrollRunResponse;
import com.acme.salary.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    /** Month-centric view: the last N months (newest first) with what "Pay" would do. */
    @GetMapping("/months")
    public List<PayrollMonthResponse> months(@RequestParam(defaultValue = "13") int months) {
        return payrollService.months(months);
    }

    /** Queues the run and returns 202 immediately; poll GET /runs/{id} for progress. */
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PayrollRunResponse queue(@Valid @RequestBody PayrollRunRequest request,
                                    Principal principal) {
        return payrollService.queue(request, principal.getName());
    }

    @GetMapping("/runs/{id}")
    public PayrollRunResponse get(@PathVariable Long id) {
        return payrollService.getRun(id);
    }

    @GetMapping("/runs")
    public PageResponse<PayrollRunResponse> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return payrollService.listRuns(page, size);
    }
}

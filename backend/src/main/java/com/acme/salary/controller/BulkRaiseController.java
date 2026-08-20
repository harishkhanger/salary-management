package com.acme.salary.controller;

import java.security.Principal;

import com.acme.salary.dto.request.BulkRaiseExecuteRequest;
import com.acme.salary.dto.request.BulkRaisePreviewRequest;
import com.acme.salary.dto.response.BulkRaisePreviewResponse;
import com.acme.salary.dto.response.BulkRaiseRunResponse;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.service.BulkRaiseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bulk-raises")
@RequiredArgsConstructor
public class BulkRaiseController {

    private final BulkRaiseService bulkRaiseService;

    @PostMapping("/preview")
    public BulkRaisePreviewResponse preview(@Valid @RequestBody BulkRaisePreviewRequest request) {
        return bulkRaiseService.preview(request);
    }

    /** Queues the run and returns 202 immediately; poll GET /{id} for progress. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BulkRaiseRunResponse execute(@Valid @RequestBody BulkRaiseExecuteRequest request,
                                        Principal principal) {
        return bulkRaiseService.queue(request, principal.getName());
    }

    @GetMapping("/{id}")
    public BulkRaiseRunResponse get(@PathVariable Long id) {
        return bulkRaiseService.getRun(id);
    }

    @GetMapping
    public PageResponse<BulkRaiseRunResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return bulkRaiseService.listRuns(page, size);
    }
}

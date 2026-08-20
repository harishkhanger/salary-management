package com.acme.salary.controller;

import com.acme.salary.dto.BulkRaiseExecuteRequest;
import com.acme.salary.dto.BulkRaisePreviewRequest;
import com.acme.salary.dto.BulkRaisePreviewResponse;
import com.acme.salary.dto.BulkRaiseRunResponse;
import com.acme.salary.dto.PageResponse;
import com.acme.salary.service.BulkRaiseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bulk-raises")
@RequiredArgsConstructor
public class BulkRaiseController {

    // @TODO placeholder until the auth increment supplies the session user
    private static final String ACTOR = "hr";

    private final BulkRaiseService bulkRaiseService;

    @PostMapping("/preview")
    public BulkRaisePreviewResponse preview(@Valid @RequestBody BulkRaisePreviewRequest request) {
        return bulkRaiseService.preview(request);
    }

    @PostMapping
    public BulkRaiseRunResponse execute(@Valid @RequestBody BulkRaiseExecuteRequest request) {
        return bulkRaiseService.execute(request, ACTOR);
    }

    @GetMapping
    public PageResponse<BulkRaiseRunResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return bulkRaiseService.listRuns(page, size);
    }
}

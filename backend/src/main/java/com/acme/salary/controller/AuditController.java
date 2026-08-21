package com.acme.salary.controller;

import com.acme.salary.dto.response.AuditFeedItem;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public PageResponse<AuditFeedItem> feed(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String entityType,
                                  @RequestParam(required = false) Long entityId,
                                  @RequestParam(required = false) Long runId,
                                  @RequestParam(required = false) String runType,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(required = false) String actor,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return auditService.feed(page, size, entityType, entityId, runId, runType,
                action, actor, from, to);
    }
}

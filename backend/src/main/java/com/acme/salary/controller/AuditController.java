package com.acme.salary.controller;

import com.acme.salary.dto.response.AuditFeedResponse;
import com.acme.salary.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public AuditFeedResponse feed(@RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "20") int limit,
                                  @RequestParam(required = false) String entityType,
                                  @RequestParam(required = false) Long entityId,
                                  @RequestParam(required = false) Long runId,
                                  @RequestParam(required = false) String runType) {
        return auditService.feed(cursor, limit, entityType, entityId, runId, runType);
    }
}

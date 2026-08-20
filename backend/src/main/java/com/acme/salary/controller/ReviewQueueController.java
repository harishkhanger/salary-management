package com.acme.salary.controller;

import java.security.Principal;

import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.response.ReviewItemResponse;
import com.acme.salary.dto.response.SalaryChangeOutcome;
import com.acme.salary.enums.ReviewStatus;
import com.acme.salary.service.ReviewQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-queue")
@RequiredArgsConstructor
public class ReviewQueueController {

    private final ReviewQueueService reviewQueueService;

    @GetMapping
    public PageResponse<ReviewItemResponse> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewQueueService.list(status, page, size);
    }

    @PostMapping("/{id}/approve")
    public SalaryChangeOutcome approve(@PathVariable Long id, Principal principal) {
        return reviewQueueService.approve(id, principal.getName());
    }

    @PostMapping("/{id}/reject")
    public ReviewItemResponse reject(@PathVariable Long id) {
        return reviewQueueService.reject(id);
    }
}

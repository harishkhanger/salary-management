package com.acme.salary.dto;

import com.acme.salary.entities.Employee;
import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.enums.ReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewItemResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String name,
        Long bulkRaiseRunId,
        BigDecimal proposedOld,
        BigDecimal proposedNew,
        String reason,
        ReviewStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
    public static ReviewItemResponse from(RaiseReviewItem item, Employee employee) {
        return new ReviewItemResponse(item.getId(), item.getEmployeeId(),
                employee != null ? employee.getEmployeeCode() : null,
                employee != null ? employee.getName() : null,
                item.getBulkRaiseRunId(), item.getProposedOld(), item.getProposedNew(),
                item.getReason(), item.getStatus(), item.getCreatedAt(), item.getResolvedAt());
    }
}

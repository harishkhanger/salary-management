package com.acme.salary.entities;

import com.acme.salary.enums.ReviewStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "raise_review_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaiseReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "bulk_raise_run_id")
    private Long bulkRaiseRunId;

    @Column(name = "proposed_old", nullable = false, precision = 15, scale = 2)
    private BigDecimal proposedOld;

    @Column(name = "proposed_new", nullable = false, precision = 15, scale = 2)
    private BigDecimal proposedNew;

    @Column(nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

package com.acme.salary.entities;

import com.acme.salary.enums.RaiseType;

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
@Table(name = "bulk_raise_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRaiseRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "raise_type", nullable = false, length = 10)
    private RaiseType raiseType;

    @Column(name = "raise_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal raiseValue;

    @Column(name = "filter_country", length = 60)
    private String filterCountry;

    @Column(name = "filter_department", length = 60)
    private String filterDepartment;

    @Column(name = "applied_count", nullable = false)
    private int appliedCount;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "excluded_count", nullable = false)
    private int excludedCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

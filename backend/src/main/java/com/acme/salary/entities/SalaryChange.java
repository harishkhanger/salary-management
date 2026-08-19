package com.acme.salary.entities;

import com.acme.salary.enums.ChangeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only: rows are inserted, never updated or deleted.
 * Corrections and reverts are new compensating entries.
 */
@Entity
@Table(name = "salary_changes")
@Getter
@Setter
public class SalaryChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "old_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal oldSalary;

    @Column(name = "new_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal newSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 12)
    private ChangeType changeType;

    @Column(name = "percent_value", precision = 5, scale = 2)
    private BigDecimal percentValue;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(name = "bulk_raise_run_id")
    private Long bulkRaiseRunId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

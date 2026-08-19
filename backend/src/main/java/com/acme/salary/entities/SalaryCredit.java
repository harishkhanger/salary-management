package com.acme.salary.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable payroll fact: snapshots amount, currency, and USD rate at credit
 * time. The unique constraint (employee_id, year, month) enforces payroll
 * idempotency at the database level.
 */
@Entity
@Table(name = "salary_credits")
@Getter
@Setter
public class SalaryCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "year", nullable = false)
    private int year;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "month", nullable = false)
    private int month;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "usd_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal usdRate;

    @Column(name = "payroll_run_id", nullable = false)
    private Long payrollRunId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

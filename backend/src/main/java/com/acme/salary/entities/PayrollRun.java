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

import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_runs")
@Getter
@Setter
public class PayrollRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "year", nullable = false)
    private int year;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "month", nullable = false)
    private int month;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "skipped_held_count", nullable = false)
    private int skippedHeldCount;

    @Column(name = "already_processed_count", nullable = false)
    private int alreadyProcessedCount;

    @Column(name = "initiated_by", nullable = false, length = 50)
    private String initiatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

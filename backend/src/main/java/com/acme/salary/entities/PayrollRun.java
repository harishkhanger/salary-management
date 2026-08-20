package com.acme.salary.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.acme.salary.enums.JobStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    /** Non-null for single-employee runs; null = whole org. */
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

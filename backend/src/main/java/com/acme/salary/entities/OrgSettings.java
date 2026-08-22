package com.acme.salary.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Single-row settings table (id is always 1, enforced by a CHECK constraint).
 */
@Entity
@Table(name = "org_settings")
@Getter
@Setter
public class OrgSettings {

    @Id
    private Long id;

    @Column(name = "raise_threshold_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal raiseThresholdPercent;

    /** Day of the month from which the current month's payroll may be processed (1–28). */
    @Column(name = "payroll_day", nullable = false)
    private int payrollDay;
}

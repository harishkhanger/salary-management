package com.acme.salary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The current month becomes processable on this day. */
@ConfigurationProperties(prefix = "app.payroll")
public record PayrollProperties(int currentMonthProcessableFromDay) {
}

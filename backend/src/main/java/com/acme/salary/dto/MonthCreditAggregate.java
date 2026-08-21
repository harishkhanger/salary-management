package com.acme.salary.dto;

import java.time.LocalDateTime;

/** GROUP BY projection: credits per period, aggregated in the database. */
public record MonthCreditAggregate(int year, int month, long creditedCount, LocalDateTime lastCreditedAt) {
}

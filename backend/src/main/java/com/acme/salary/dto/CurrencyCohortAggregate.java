package com.acme.salary.dto;

import java.math.BigDecimal;

/** JPQL constructor projection: cohort headcount and salary total per currency. */
public record CurrencyCohortAggregate(String currencyCode, long headcount, BigDecimal totalSalary) {
}

package com.acme.salary.analytics;

import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.entities.Employee;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.repository.AnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB contract: analytics numbers are computed by SQL aggregates (GROUP BY +
 * window functions) with USD normalization — including the portable median
 * (no MEDIAN() in MySQL) for both odd and even group sizes.
 */
@DataJpaTest
class AnalyticsAggregatesDbContractTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @BeforeEach
    void seed() {
        currency("USD", "1.000000");
        currency("INR", "80.000000");
        // Engineering (USD): 60k, 100k, 140k -> avg 100k, median 100k (odd count)
        employee("E1", "US", "Engineering", "USD", "60000", EmployeeStatus.ACTIVE, false);
        employee("E2", "US", "Engineering", "USD", "100000", EmployeeStatus.ACTIVE, false);
        employee("E3", "US", "Engineering", "USD", "140000", EmployeeStatus.ON_HOLD, false);
        // Sales (INR): 4M/80=50k, 8M/80=100k -> avg 75k, median 75k (even count)
        employee("S1", "India", "Sales", "INR", "4000000", EmployeeStatus.ACTIVE, false);
        employee("S2", "India", "Sales", "INR", "8000000", EmployeeStatus.ACTIVE, false);
        // deleted employees never count
        employee("X1", "US", "Engineering", "USD", "999999", EmployeeStatus.ACTIVE, true);
    }

    @Test
    void summaryCountsAndUsdNormalizedMonthlySpend() {
        AnalyticsRepository.SummaryRow row = analyticsRepository.summarize();

        assertThat(row.getHeadcount()).isEqualTo(5);
        assertThat(row.getOnHoldCount()).isEqualTo(1);
        // annual USD total = 60k+100k+140k+50k+100k = 450k -> monthly 37500
        assertThat(row.getTotalMonthlySpendUsd()).isEqualByComparingTo("37500.00");
    }

    @Test
    void spendByCountryGroupsAndNormalizes() {
        List<AnalyticsRepository.CountryRow> rows = analyticsRepository.spendByCountry();

        assertThat(rows).hasSize(2);
        var us = rows.stream().filter(r -> r.getCountry().equals("US")).findFirst().orElseThrow();
        assertThat(us.getHeadcount()).isEqualTo(3);
        assertThat(us.getMonthlySpendUsd()).isEqualByComparingTo("25000.00"); // 300k/12
        var india = rows.stream().filter(r -> r.getCountry().equals("India")).findFirst().orElseThrow();
        assertThat(india.getMonthlySpendUsd()).isEqualByComparingTo("12500.00"); // 150k/12
    }

    @Test
    void departmentMedianHandlesOddAndEvenGroupSizes() {
        var medians = analyticsRepository.medianByDepartment();
        var averages = analyticsRepository.averageByDepartment();

        var engMedian = medians.stream()
                .filter(r -> r.getDepartment().equals("Engineering")).findFirst().orElseThrow();
        assertThat(engMedian.getMedianAnnualUsd()).isEqualByComparingTo("100000.00"); // odd: middle
        var salesMedian = medians.stream()
                .filter(r -> r.getDepartment().equals("Sales")).findFirst().orElseThrow();
        assertThat(salesMedian.getMedianAnnualUsd()).isEqualByComparingTo("75000.00"); // even: avg of middle two

        var engAvg = averages.stream()
                .filter(r -> r.getDepartment().equals("Engineering")).findFirst().orElseThrow();
        assertThat(engAvg.getAvgAnnualUsd()).isEqualByComparingTo("100000.00");
        assertThat(engAvg.getHeadcount()).isEqualTo(3);
    }

    @Test
    void distributionBucketsBySalaryBand() {
        List<AnalyticsRepository.BucketRow> rows = analyticsRepository.salaryDistribution(50000);

        // 50k -> [50k,100k); 60k -> [50k,100k); 100k x2 -> [100k,150k); 140k -> [100k,150k)
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().getBucketFloorUsd()).isEqualByComparingTo("50000");
        assertThat(rows.getFirst().getEmployeeCount()).isEqualTo(2);
        assertThat(rows.getLast().getBucketFloorUsd()).isEqualByComparingTo("100000");
        assertThat(rows.getLast().getEmployeeCount()).isEqualTo(3);
    }

    private void currency(String code, String rate) {
        CurrencyRate c = new CurrencyRate();
        c.setCode(code);
        c.setName(code);
        c.setUsdRate(new BigDecimal(rate));
        c.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        em.persist(c);
    }

    private void employee(String code, String country, String department, String currency,
                          String salary, EmployeeStatus status, boolean deleted) {
        Employee e = Employee.builder()
                .employeeCode(code).name(code).email(code + "@acme.test")
                .country(country).department(department).currencyCode(currency)
                .annualSalary(new BigDecimal(salary)).status(status)
                .joinedOn(LocalDate.of(2024, 1, 1)).deleted(deleted)
                .build();
        em.persist(e);
    }
}

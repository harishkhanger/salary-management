package com.acme.salary.repository;

import com.acme.salary.entities.Employee;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Analytics aggregates — every number is computed by the database (GROUP BY /
 * window functions); the 10k employee rows are never loaded into memory.
 * USD normalization uses CURRENT rates (usd_rate = local per 1 USD -> divide).
 * Native SQL kept portable across MySQL 8 and the H2 test slice.
 */
public interface AnalyticsRepository extends Repository<Employee, Long> {

    interface SummaryRow {
        long getHeadcount();

        long getOnHoldCount();

        BigDecimal getTotalMonthlySpendUsd();
    }

    interface CountryRow {
        String getCountry();

        long getHeadcount();

        BigDecimal getMonthlySpendUsd();
    }

    interface DepartmentAvgRow {
        String getDepartment();

        long getHeadcount();

        BigDecimal getAvgAnnualUsd();
    }

    interface DepartmentMedianRow {
        String getDepartment();

        BigDecimal getMedianAnnualUsd();
    }

    /** Pay statistics for one group: the four numbers an HR manager asks for. */
    interface PayStatsRow {
        String getLabel();

        long getHeadcount();

        BigDecimal getMinUsd();

        BigDecimal getMaxUsd();

        BigDecimal getAvgUsd();

        BigDecimal getMedianUsd();
    }

    interface BucketRow {
        BigDecimal getBucketFloorUsd();

        long getEmployeeCount();
    }

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*)                                              AS headcount,
                   COALESCE(SUM(CASE WHEN e.status = 'ON_HOLD' THEN 1 ELSE 0 END), 0) AS onHoldCount,
                   COALESCE(ROUND(SUM(e.annual_salary / c.usd_rate / 12), 2), 0)      AS totalMonthlySpendUsd
            FROM employees e
            JOIN currency_rates c ON c.code = e.currency_code
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
            """)
    SummaryRow summarize(@Param("country") String country, @Param("department") String department);

    @Query(nativeQuery = true, value = """
            SELECT e.country                                        AS country,
                   COUNT(*)                                         AS headcount,
                   ROUND(SUM(e.annual_salary / c.usd_rate / 12), 2) AS monthlySpendUsd
            FROM employees e
            JOIN currency_rates c ON c.code = e.currency_code
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
            GROUP BY e.country
            ORDER BY monthlySpendUsd DESC
            """)
    List<CountryRow> spendByCountry(@Param("country") String country, @Param("department") String department);

    @Query(nativeQuery = true, value = """
            SELECT e.department                                 AS department,
                   COUNT(*)                                     AS headcount,
                   ROUND(AVG(e.annual_salary / c.usd_rate), 2)  AS avgAnnualUsd
            FROM employees e
            JOIN currency_rates c ON c.code = e.currency_code
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
            GROUP BY e.department
            ORDER BY e.department
            """)
    List<DepartmentAvgRow> averageByDepartment(@Param("country") String country, @Param("department") String department);

    /**
     * Portable median (MySQL has no MEDIAN/percentile_cont): rank each salary
     * inside its department, keep the middle row (or the middle two when the
     * count is even) and average them.
     */
    @Query(nativeQuery = true, value = """
            SELECT t.department AS department, ROUND(AVG(t.usd), 2) AS medianAnnualUsd
            FROM (SELECT e.department AS department,
                         e.annual_salary / c.usd_rate AS usd,
                         ROW_NUMBER() OVER (PARTITION BY e.department
                                            ORDER BY e.annual_salary / c.usd_rate, e.id) AS rn,
                         COUNT(*) OVER (PARTITION BY e.department) AS cnt
                  FROM employees e
                  JOIN currency_rates c ON c.code = e.currency_code
                  WHERE e.deleted = false
                    AND (:country IS NULL OR e.country = :country)
                    AND (:department IS NULL OR e.department = :department)) t
            WHERE t.rn IN (FLOOR((t.cnt + 1) / 2.0), CEILING((t.cnt + 1) / 2.0))
            GROUP BY t.department
            """)
    List<DepartmentMedianRow> medianByDepartment(@Param("country") String country, @Param("department") String department);

    /**
     * Min / max / average / median annual USD per country (or per department),
     * optionally restricted to a set of countries and one department. The
     * median is the same portable window form as medianByDepartment; the
     * country filter is a flag + list because SQL has no "empty IN list".
     */
    @Query(nativeQuery = true, value = """
            SELECT t.grp                                         AS label,
                   COUNT(*)                                      AS headcount,
                   ROUND(MIN(t.usd), 2)                          AS minUsd,
                   ROUND(MAX(t.usd), 2)                          AS maxUsd,
                   ROUND(AVG(t.usd), 2)                          AS avgUsd,
                   ROUND(AVG(CASE WHEN t.rn IN (FLOOR((t.cnt + 1) / 2.0), CEILING((t.cnt + 1) / 2.0))
                                  THEN t.usd END), 2)            AS medianUsd
            FROM (SELECT e.country AS grp,
                         e.annual_salary / c.usd_rate AS usd,
                         ROW_NUMBER() OVER (PARTITION BY e.country
                                            ORDER BY e.annual_salary / c.usd_rate, e.id) AS rn,
                         COUNT(*) OVER (PARTITION BY e.country) AS cnt
                  FROM employees e
                  JOIN currency_rates c ON c.code = e.currency_code
                  WHERE e.deleted = false
                    AND (:filterCountries = 0 OR e.country IN (:countries))
                    AND (:department IS NULL OR e.department = :department)) t
            GROUP BY t.grp
            ORDER BY t.grp
            """)
    List<PayStatsRow> payStatsByCountry(@Param("filterCountries") int filterCountries,
                                        @Param("countries") List<String> countries,
                                        @Param("department") String department);

    @Query(nativeQuery = true, value = """
            SELECT t.grp                                         AS label,
                   COUNT(*)                                      AS headcount,
                   ROUND(MIN(t.usd), 2)                          AS minUsd,
                   ROUND(MAX(t.usd), 2)                          AS maxUsd,
                   ROUND(AVG(t.usd), 2)                          AS avgUsd,
                   ROUND(AVG(CASE WHEN t.rn IN (FLOOR((t.cnt + 1) / 2.0), CEILING((t.cnt + 1) / 2.0))
                                  THEN t.usd END), 2)            AS medianUsd
            FROM (SELECT e.department AS grp,
                         e.annual_salary / c.usd_rate AS usd,
                         ROW_NUMBER() OVER (PARTITION BY e.department
                                            ORDER BY e.annual_salary / c.usd_rate, e.id) AS rn,
                         COUNT(*) OVER (PARTITION BY e.department) AS cnt
                  FROM employees e
                  JOIN currency_rates c ON c.code = e.currency_code
                  WHERE e.deleted = false
                    AND (:filterCountries = 0 OR e.country IN (:countries))
                    AND (:department IS NULL OR e.department = :department)) t
            GROUP BY t.grp
            ORDER BY t.grp
            """)
    List<PayStatsRow> payStatsByDepartment(@Param("filterCountries") int filterCountries,
                                           @Param("countries") List<String> countries,
                                           @Param("department") String department);

    @Query(nativeQuery = true, value = """
            SELECT FLOOR(e.annual_salary / c.usd_rate / :bucketUsd) * :bucketUsd AS bucketFloorUsd,
                   COUNT(*)                                                      AS employeeCount
            FROM employees e
            JOIN currency_rates c ON c.code = e.currency_code
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
            GROUP BY bucketFloorUsd
            ORDER BY bucketFloorUsd
            """)
    List<BucketRow> salaryDistribution(@Param("bucketUsd") int bucketUsd,
                                       @Param("country") String country,
                                       @Param("department") String department);

    /**
     * Custom range ("who earns between 40k and 42k?"): bands start at minUsd
     * rather than at 0 so a 500-wide band reads 40,000–40,500; bounds are
     * inclusive. The service fills empty bands and folds a salary sitting
     * exactly on maxUsd into the last band.
     */
    @Query(nativeQuery = true, value = """
            SELECT FLOOR((e.annual_salary / c.usd_rate - :minUsd) / :bucketUsd) * :bucketUsd + :minUsd AS bucketFloorUsd,
                   COUNT(*)                                                                          AS employeeCount
            FROM employees e
            JOIN currency_rates c ON c.code = e.currency_code
            WHERE e.deleted = false
              AND (:country IS NULL OR e.country = :country)
              AND (:department IS NULL OR e.department = :department)
              AND e.annual_salary / c.usd_rate >= :minUsd
              AND e.annual_salary / c.usd_rate <= :maxUsd
            GROUP BY bucketFloorUsd
            ORDER BY bucketFloorUsd
            """)
    List<BucketRow> salaryDistributionInRange(@Param("bucketUsd") int bucketUsd,
                                              @Param("minUsd") int minUsd,
                                              @Param("maxUsd") int maxUsd,
                                              @Param("country") String country,
                                              @Param("department") String department);
}

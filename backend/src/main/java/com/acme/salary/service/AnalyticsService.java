package com.acme.salary.service;

import com.acme.salary.config.AnalyticsProperties;
import com.acme.salary.dto.response.AnalyticsResponses.CountrySpend;
import com.acme.salary.dto.response.AnalyticsResponses.DepartmentStats;
import com.acme.salary.dto.response.AnalyticsResponses.PayStats;
import com.acme.salary.dto.response.AnalyticsResponses.Distribution;
import com.acme.salary.dto.response.AnalyticsResponses.SalaryBucket;
import com.acme.salary.dto.response.AnalyticsResponses.Summary;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.repository.AnalyticsRepository;
import com.acme.salary.repository.AnalyticsRepository.DepartmentMedianRow;
import com.acme.salary.repository.AnalyticsRepository.SummaryRow;
import com.acme.salary.exception.ValidationException;
import com.acme.salary.repository.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Optional country/department filters apply to every endpoint so the
 * dashboard can slice the numbers; null/blank = whole org.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final AnalyticsProperties analyticsProperties;

    @Transactional(readOnly = true)
    public Summary summary(String country, String department) {
        SummaryRow row = analyticsRepository.summarize(blankToNull(country), blankToNull(department));
        Summary.LastPayrollRun lastRun = payrollRunRepository
                .findFirstByStatusOrderByCreatedAtDesc(JobStatus.COMPLETED)
                .map(run -> new Summary.LastPayrollRun(run.getYear(), run.getMonth(),
                        run.getProcessedCount(), run.getCreatedAt()))
                .orElse(null);
        return new Summary(row.getTotalMonthlySpendUsd(), row.getHeadcount(),
                row.getOnHoldCount(), lastRun);
    }

    @Transactional(readOnly = true)
    public List<CountrySpend> byCountry(String country, String department) {
        return analyticsRepository.spendByCountry(blankToNull(country), blankToNull(department))
                .stream()
                .map(row -> new CountrySpend(row.getCountry(), row.getHeadcount(),
                        row.getMonthlySpendUsd()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentStats> byDepartment(String country, String department) {
        Map<String, BigDecimal> medians = analyticsRepository
                .medianByDepartment(blankToNull(country), blankToNull(department)).stream()
                .collect(Collectors.toMap(DepartmentMedianRow::getDepartment,
                        DepartmentMedianRow::getMedianAnnualUsd));
        return analyticsRepository
                .averageByDepartment(blankToNull(country), blankToNull(department)).stream()
                .map(row -> new DepartmentStats(row.getDepartment(), row.getHeadcount(),
                        row.getAvgAnnualUsd(), medians.get(row.getDepartment())))
                .toList();
    }

    /**
     * "What do people earn in India vs Germany?" — min/max/avg/median per
     * country or per department, for any set of countries and an optional
     * department. Aggregated in the database like everything else here.
     */
    @Transactional(readOnly = true)
    public List<PayStats> payStats(String groupBy, List<String> countries, String department) {
        List<String> wanted = countries == null ? List.of()
                : countries.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        int filter = wanted.isEmpty() ? 0 : 1;
        // an IN list must not be empty even when the flag disables it
        List<String> inList = wanted.isEmpty() ? List.of("") : wanted;
        List<AnalyticsRepository.PayStatsRow> rows = switch (groupBy == null ? "country" : groupBy) {
            case "country" -> analyticsRepository.payStatsByCountry(filter, inList, blankToNull(department));
            case "department" -> analyticsRepository.payStatsByDepartment(filter, inList, blankToNull(department));
            default -> throw new ValidationException("groupBy must be 'country' or 'department'");
        };
        return rows.stream()
                .map(r -> new PayStats(r.getLabel(), r.getHeadcount(), r.getMinUsd(), r.getMaxUsd(),
                        r.getAvgUsd(), r.getMedianUsd()))
                .toList();
    }

    /** Whole-org histogram: band widths the dashboard may request; anything else is rejected. */
    private static final List<Integer> ALLOWED_BUCKETS = List.of(5_000, 10_000, 20_000, 50_000);
    /** Custom range: any width from this down to this many bands — keeps the chart readable and the query bounded. */
    private static final int MIN_RANGE_BUCKET_USD = 100;
    private static final int MAX_RANGE_BANDS = 200;
    private static final int TARGET_RANGE_BANDS = 10;

    @Transactional(readOnly = true)
    public Distribution salaryDistribution(String country, String department, Integer requestedBucketUsd,
                                           Integer minUsd, Integer maxUsd) {
        if (minUsd != null || maxUsd != null) {
            return rangeDistribution(country, department, requestedBucketUsd, minUsd, maxUsd);
        }
        int bucketUsd = requestedBucketUsd != null ? requestedBucketUsd
                : analyticsProperties.distributionBucketUsd();
        if (!ALLOWED_BUCKETS.contains(bucketUsd)) {
            throw new ValidationException("bucketUsd must be one of " + ALLOWED_BUCKETS);
        }
        List<SalaryBucket> buckets = analyticsRepository
                .salaryDistribution(bucketUsd, blankToNull(country), blankToNull(department)).stream()
                .map(row -> new SalaryBucket(row.getBucketFloorUsd(),
                        row.getBucketFloorUsd().add(BigDecimal.valueOf(bucketUsd)),
                        row.getEmployeeCount()))
                .toList();
        long total = buckets.stream().mapToLong(SalaryBucket::count).sum();
        return new Distribution(bucketUsd, null, null, total, buckets);
    }

    /**
     * "How many people earn between X and Y?" — same SQL aggregate, bands
     * anchored at X. Width defaults to roughly ten bands, rounded to a
     * sensible figure; empty bands are filled so the chart stays contiguous.
     */
    private Distribution rangeDistribution(String country, String department, Integer requestedBucketUsd,
                                           Integer minUsd, Integer maxUsd) {
        if (minUsd == null || maxUsd == null) {
            throw new ValidationException("Give both a minimum and a maximum salary for the range");
        }
        if (minUsd < 0 || maxUsd < minUsd) {
            throw new ValidationException("The maximum salary must not be less than the minimum");
        }
        int range = maxUsd - minUsd;
        if (range == 0) {
            // "who earns exactly X?" — one band, bounds inclusive
            long count = analyticsRepository.salaryDistributionInRange(1, minUsd, maxUsd,
                            blankToNull(country), blankToNull(department)).stream()
                    .mapToLong(AnalyticsRepository.BucketRow::getEmployeeCount).sum();
            return new Distribution(0, minUsd, maxUsd, count,
                    List.of(new SalaryBucket(BigDecimal.valueOf(minUsd), BigDecimal.valueOf(maxUsd), count)));
        }
        int bucketUsd = requestedBucketUsd != null ? requestedBucketUsd : defaultRangeBucket(range);
        if (bucketUsd < MIN_RANGE_BUCKET_USD) {
            throw new ValidationException("Band width must be at least " + MIN_RANGE_BUCKET_USD);
        }
        // a band as wide as (or wider than) the range is the "one bar" question:
        // "how many earn between X and Y?" — answer with a single band
        bucketUsd = Math.min(bucketUsd, range);
        if (range / bucketUsd > MAX_RANGE_BANDS) {
            throw new ValidationException("That band width gives more than " + MAX_RANGE_BANDS
                    + " bands — widen it or narrow the range");
        }

        Map<Integer, Long> counted = new java.util.HashMap<>();
        for (var row : analyticsRepository.salaryDistributionInRange(bucketUsd, minUsd, maxUsd,
                blankToNull(country), blankToNull(department))) {
            counted.merge(row.getBucketFloorUsd().intValue(), row.getEmployeeCount(), Long::sum);
        }
        List<SalaryBucket> buckets = new java.util.ArrayList<>();
        for (int floor = minUsd; floor < maxUsd; floor += bucketUsd) {
            int ceiling = Math.min(floor + bucketUsd, maxUsd);
            long count = counted.getOrDefault(floor, 0L);
            if (ceiling == maxUsd) {
                // a salary sitting exactly on the maximum is inside the range: fold it into the last band
                count += counted.getOrDefault(maxUsd, 0L);
            }
            buckets.add(new SalaryBucket(BigDecimal.valueOf(floor), BigDecimal.valueOf(ceiling), count));
        }
        long total = buckets.stream().mapToLong(SalaryBucket::count).sum();
        return new Distribution(bucketUsd, minUsd, maxUsd, total, buckets);
    }

    /** ~10 bands, rounded down to 1/2/5 x 10^n so labels read cleanly (2,000 not 2,137). */
    public static int defaultRangeBucket(int range) {
        int raw = Math.max(range / TARGET_RANGE_BANDS, MIN_RANGE_BUCKET_USD);
        int magnitude = (int) Math.pow(10, (int) Math.log10(raw));
        int leading = raw / magnitude;
        int nice = leading >= 5 ? 5 : leading >= 2 ? 2 : 1;
        return Math.max(nice * magnitude, MIN_RANGE_BUCKET_USD);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

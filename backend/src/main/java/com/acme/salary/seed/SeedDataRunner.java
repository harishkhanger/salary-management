package com.acme.salary.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Synthetic dataset: ~10k employees across 10 countries/currencies with
 * realistic distributions, plus 12 months of simulated history (monthly
 * payroll runs, scattered raises, bulk-raise runs, review items, holds,
 * deletions) — every event with a consistent audit row, so the keyset feed,
 * run collapse, and analytics are demonstrable at scale.
 *
 * Runs under the "seed" profile with plain JDBC batch inserts and explicit
 * id counters (the cross-table ref graph — changes/credits <- audit thin
 * refs — is built in memory). Deterministic via a fixed Random seed.
 *
 *   docker compose run --rm -e SPRING_PROFILES_ACTIVE=seed backend
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class SeedDataRunner implements CommandLineRunner {

    private static final int EMPLOYEES = 10_000;
    private static final int MONTHS = 12;
    private static final int BATCH = 1_000;
    private static final String ACTOR = "hr";

    private final JdbcTemplate jdbc;
    private final ConfigurableApplicationContext context;

    private final Random random = new Random(42);

    // currency -> local units per 1 USD (final month = exactly these)
    private static final Object[][] CURRENCIES = {
            {"USD", "US Dollar", "1.000000"}, {"INR", "Indian Rupee", "83.500000"},
            {"EUR", "Euro", "0.920000"}, {"GBP", "Pound Sterling", "0.790000"},
            {"JPY", "Japanese Yen", "148.000000"}, {"AUD", "Australian Dollar", "1.520000"},
            {"CAD", "Canadian Dollar", "1.360000"}, {"SGD", "Singapore Dollar", "1.340000"},
            {"AED", "UAE Dirham", "3.670000"}, {"BRL", "Brazilian Real", "5.100000"},
    };

    // country, currency, weight, median annual salary in USD
    private static final Object[][] COUNTRIES = {
            {"India", "INR", 30, 28_000}, {"United States", "USD", 22, 125_000},
            {"Germany", "EUR", 9, 78_000}, {"United Kingdom", "GBP", 9, 72_000},
            {"Japan", "JPY", 7, 62_000}, {"Canada", "CAD", 6, 80_000},
            {"Australia", "AUD", 6, 85_000}, {"Singapore", "SGD", 5, 90_000},
            {"UAE", "AED", 3, 75_000}, {"Brazil", "BRL", 3, 35_000},
    };

    private static final Object[][] DEPARTMENTS = {
            {"Engineering", 34, 1.25}, {"Sales", 15, 1.0}, {"Operations", 12, 0.85},
            {"Support", 10, 0.7}, {"Marketing", 8, 0.95}, {"Finance", 7, 1.1},
            {"Product", 5, 1.3}, {"HR", 5, 0.85}, {"Legal", 2, 1.35}, {"Design", 2, 1.05},
    };

    private static final String[] FIRST_NAMES = {
            "Aarav", "Ananya", "Arjun", "Divya", "Ishaan", "Kavya", "Rahul", "Priya", "Vikram", "Sneha",
            "James", "Emily", "Michael", "Sarah", "David", "Jessica", "Daniel", "Ashley", "Matthew", "Amanda",
            "Lukas", "Anna", "Felix", "Lena", "Jonas", "Marie", "Oliver", "Emma", "Harry", "Sophie",
            "Haruto", "Yui", "Sota", "Sakura", "Ren", "Mio", "Liam", "Charlotte", "Noah", "Olivia",
            "Ethan", "Chloe", "Lucas", "Mia", "Wei", "Xiu", "Omar", "Fatima", "Pedro", "Ana",
            "Carlos", "Julia", "Rafael", "Beatriz", "Hassan", "Layla", "Tariq", "Noor", "Kenji", "Aiko",
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Patel", "Kumar", "Singh", "Reddy", "Iyer", "Nair", "Gupta", "Mehta", "Joshi",
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson", "Taylor", "Clark",
            "Mueller", "Schmidt", "Weber", "Fischer", "Wagner", "Becker", "Hoffmann", "Koch", "Richter", "Wolf",
            "Tanaka", "Suzuki", "Sato", "Watanabe", "Ito", "Yamamoto", "Nakamura", "Kobayashi", "Kato", "Yoshida",
            "Martin", "Thompson", "White", "Harris", "Lewis", "Walker", "Hall", "Young", "King", "Wright",
            "Silva", "Santos", "Oliveira", "Souza", "Costa", "Al-Farsi", "Haddad", "Khan", "Rahman", "Osman",
    };

    /* ---- id counters (explicit ids keep the in-memory ref graph consistent) ---- */
    private long employeeId;
    private long changeId;
    private long creditId;
    private long payrollRunId;
    private long bulkRunId;
    private long reviewItemId;

    /* ---- batch buffers ---- */
    private final List<Object[]> employeeRows = new ArrayList<>();
    private final List<Object[]> changeRows = new ArrayList<>();
    private final List<Object[]> creditRows = new ArrayList<>();
    private final List<Object[]> reviewRows = new ArrayList<>();
    private final List<Object[]> auditRows = new ArrayList<>();

    @Override
    public void run(String... args) {
        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM employees", Long.class);
        if (existing != null && existing >= 1000) {
            log.info("Seed skipped: {} employees already present", existing);
            shutdown();
            return;
        }
        long started = System.currentTimeMillis();
        log.info("Seeding {} employees with {} months of history...", EMPLOYEES, MONTHS);

        initCounters();
        seedCurrencies();

        YearMonth firstMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(MONTHS);
        Map<String, BigDecimal[]> monthlyRates = driftRates(firstMonth);
        long[] payrollRunIds = insertPayrollRuns(firstMonth);
        int[] payrollCounts = new int[MONTHS];
        BulkRun[] bulkRuns = insertBulkRuns(firstMonth);

        for (int i = 0; i < EMPLOYEES; i++) {
            generateEmployee(i, firstMonth, monthlyRates, payrollRunIds, payrollCounts, bulkRuns);
            if (employeeRows.size() >= BATCH) {
                flushAll();
            }
        }
        flushAll();

        finalisePayrollRuns(payrollRunIds, payrollCounts, firstMonth);
        finaliseBulkRuns(bulkRuns);
        logCounts(started);
        shutdown();
    }

    /* ------------------------------------------------------------------ */

    private void initCounters() {
        employeeId = nextId("employees");
        changeId = nextId("salary_changes");
        creditId = nextId("salary_credits");
        payrollRunId = nextId("payroll_runs");
        bulkRunId = nextId("bulk_raise_runs");
        reviewItemId = nextId("raise_review_items");
    }

    private long nextId(String table) {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return (max == null ? 0 : max) + 1;
    }

    private void seedCurrencies() {
        for (Object[] c : CURRENCIES) {
            jdbc.update("""
                    INSERT INTO currency_rates (code, name, usd_rate, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE usd_rate = VALUES(usd_rate)
                    """, c[0], c[1], new BigDecimal((String) c[2]), utcNow());
        }
    }

    /** Per-currency monthly rate factors: a small random walk that ends at exactly the current rate. */
    private Map<String, BigDecimal[]> driftRates(YearMonth firstMonth) {
        Map<String, BigDecimal[]> rates = new HashMap<>();
        for (Object[] c : CURRENCIES) {
            BigDecimal current = new BigDecimal((String) c[2]);
            BigDecimal[] monthly = new BigDecimal[MONTHS];
            double drift = (random.nextDouble() - 0.5) * 0.06; // start up to ±3% away
            for (int m = 0; m < MONTHS; m++) {
                double remaining = 1 - (double) m / (MONTHS - 1);
                double factor = 1 + drift * remaining + (random.nextDouble() - 0.5) * 0.004;
                if (m == MONTHS - 1) factor = 1; // final month = current rate exactly
                monthly[m] = current.multiply(BigDecimal.valueOf(factor)).setScale(6, RoundingMode.HALF_UP);
            }
            rates.put((String) c[0], monthly);
        }
        return rates;
    }

    private long[] insertPayrollRuns(YearMonth firstMonth) {
        long[] ids = new long[MONTHS];
        for (int m = 0; m < MONTHS; m++) {
            YearMonth ym = firstMonth.plusMonths(m);
            ids[m] = payrollRunId++;
            jdbc.update("""
                    INSERT INTO payroll_runs (id, year, month, processed_count, skipped_held_count,
                        already_processed_count, initiated_by, status, created_at)
                    VALUES (?, ?, ?, 0, 0, 0, ?, 'COMPLETED', ?)
                    """, ids[m], ym.getYear(), ym.getMonthValue(), ACTOR, runTime(ym));
        }
        return ids;
    }

    private record BulkRun(long id, int month, String department, BigDecimal percent,
                           int[] applied, int[] review) {
    }

    private BulkRun[] insertBulkRuns(YearMonth firstMonth) {
        int[] months = {2, 5, 8, 11};
        String[] departments = {"Engineering", "Sales", "Support", "Operations"};
        BulkRun[] runs = new BulkRun[months.length];
        for (int i = 0; i < months.length; i++) {
            BigDecimal percent = BigDecimal.valueOf(4 + random.nextInt(5)); // 4-8%
            runs[i] = new BulkRun(bulkRunId++, months[i], departments[i], percent, new int[1], new int[1]);
            jdbc.update("""
                    INSERT INTO bulk_raise_runs (id, raise_type, raise_value, filter_country,
                        filter_department, applied_count, review_count, excluded_count,
                        status, excluded_ids, initiated_by, created_at)
                    VALUES (?, 'PERCENT', ?, NULL, ?, 0, 0, 0, 'COMPLETED', NULL, ?, ?)
                    """, runs[i].id, percent, departments[i], ACTOR,
                    changeTime(firstMonth.plusMonths(months[i])));
        }
        return runs;
    }

    /* ------------------------------------------------------------------ */

    private void generateEmployee(int index, YearMonth firstMonth, Map<String, BigDecimal[]> monthlyRates,
                                  long[] payrollRunIds, int[] payrollCounts, BulkRun[] bulkRuns) {
        long id = employeeId++;
        Object[] country = pickWeighted(COUNTRIES, 2);
        Object[] department = pickWeighted(DEPARTMENTS, 1);
        String currency = (String) country[1];
        BigDecimal currentRate = new BigDecimal(rateOf(currency));

        // salary: log-normal-ish around the country median, scaled by department
        double medianUsd = ((Integer) country[3]).doubleValue() * (Double) department[2];
        double salaryUsd = medianUsd * Math.exp((random.nextGaussian()) * 0.45);
        salaryUsd = Math.max(medianUsd * 0.3, Math.min(medianUsd * 4.5, salaryUsd));
        BigDecimal salary = BigDecimal.valueOf(salaryUsd).multiply(currentRate)
                .setScale(2, RoundingMode.HALF_UP);

        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String name = firstName + " " + lastName;
        String email = (firstName + "." + lastName + "." + id + "@acme-corp.com").toLowerCase();
        String code = "EMP-%05d".formatted(id);

        LocalDate joined = LocalDate.now(ZoneOffset.UTC).minusDays(90 + random.nextInt(365 * 7));
        boolean deleted = random.nextInt(100) < 2;
        boolean onHold = !deleted && random.nextInt(100) < 1;

        // audit: creation (backdated to the join date)
        audit("EMPLOYEE", id, "CREATED", ACTOR, null, null, null, null, joined.atTime(9, 0));

        // salary timeline across the window
        YearMonth joinedMonth = YearMonth.from(joined);
        int creditStart = joinedMonth.isBefore(firstMonth) ? 0
                : (int) firstMonth.until(joinedMonth, java.time.temporal.ChronoUnit.MONTHS) + 1;

        // raises: 0-2 individual + possibly one bulk run hit
        List<int[]> raiseMonths = new ArrayList<>(); // [month, bulkRunIndex or -1]
        int individualRaises = random.nextInt(3);
        for (int r = 0; r < individualRaises; r++) {
            raiseMonths.add(new int[]{1 + random.nextInt(MONTHS - 1), -1});
        }
        for (int b = 0; b < bulkRuns.length; b++) {
            if (bulkRuns[b].department.equals(department[0]) && random.nextInt(100) < 70) {
                raiseMonths.add(new int[]{bulkRuns[b].month, b});
            }
        }
        // no raises before the employee joined
        raiseMonths.removeIf(r -> r[0] < creditStart);
        raiseMonths.sort((a, b) -> Integer.compare(a[0], b[0]));

        BigDecimal salaryAt = salary;
        Map<Integer, BigDecimal> salaryByMonth = new HashMap<>();
        for (int m = 0; m < MONTHS; m++) {
            for (int[] raise : raiseMonths) {
                if (raise[0] == m) {
                    BigDecimal percent = raise[1] >= 0 ? bulkRuns[raise[1]].percent
                            : BigDecimal.valueOf(3 + random.nextInt(10));
                    BigDecimal newSalary = salaryAt.multiply(BigDecimal.ONE.add(
                            percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                            .setScale(2, RoundingMode.HALF_UP);
                    Long runRef = raise[1] >= 0 ? bulkRuns[raise[1]].id : null;
                    long cid = changeId++;
                    LocalDateTime when = changeTime(firstMonth.plusMonths(m));
                    changeRows.add(new Object[]{cid, id, salaryAt, newSalary, "PERCENT", percent,
                            ACTOR, runRef, when});
                    audit("EMPLOYEE", id, "SALARY_CHANGED", ACTOR, null,
                            "salary_changes", cid, runRef, when);
                    if (raise[1] >= 0) bulkRuns[raise[1]].applied[0]++;
                    salaryAt = newSalary;
                }
            }
            salaryByMonth.put(m, salaryAt);
        }

        // a few employees get parked review items instead of one more raise
        if (random.nextInt(100) < 2) {
            int m = MONTHS - 1 - random.nextInt(3);
            BigDecimal proposed = salaryAt.multiply(BigDecimal.valueOf(1.35))
                    .setScale(2, RoundingMode.HALF_UP);
            String status = switch (random.nextInt(3)) {
                case 0 -> "PENDING";
                case 1 -> "APPROVED";
                default -> "REJECTED";
            };
            long rid = reviewItemId++;
            LocalDateTime when = changeTime(firstMonth.plusMonths(m));
            // APPROVED items in history would have changed salary; keep seeded ones PENDING/REJECTED
            String effective = status.equals("APPROVED") ? "REJECTED" : status;
            reviewRows.add(new Object[]{rid, id, null, salaryAt, proposed,
                    "Cumulative 12-month raise exceeds threshold 30.00%", effective,
                    effective.equals("PENDING") ? null : when.plusDays(2), when});
            audit("EMPLOYEE", id, "RAISE_PARKED", ACTOR, null, "raise_review_items", rid, null, when);
            if (!effective.equals("PENDING")) {
                audit("EMPLOYEE", id, "RAISE_REJECTED", ACTOR, null, "raise_review_items", rid, null,
                        when.plusDays(2));
            }
        }

        // monthly credits with the salary AND rate of that month (snapshot story)
        for (int m = creditStart; m < MONTHS; m++) {
            BigDecimal monthSalary = salaryByMonth.get(m);
            BigDecimal rate = monthlyRates.get(currency)[m];
            long crid = creditId++;
            YearMonth ym = firstMonth.plusMonths(m);
            LocalDateTime when = runTime(ym).plusMinutes(random.nextInt(50));
            creditRows.add(new Object[]{crid, id, ym.getYear(), ym.getMonthValue(),
                    monthSalary.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP),
                    currency, rate, payrollRunIds[m], when});
            audit("EMPLOYEE", id, "SALARY_CREDITED", "system", null, "salary_credits", crid,
                    payrollRunIds[m], when);
            payrollCounts[m]++;
        }

        if (onHold) {
            audit("EMPLOYEE", id, "STATUS_CHANGED", ACTOR,
                    "{\"status\":{\"old\":\"ACTIVE\",\"new\":\"ON_HOLD\"}}", null, null, null,
                    utcNow().minusDays(random.nextInt(20)));
        }
        if (deleted) {
            audit("EMPLOYEE", id, "DELETED", ACTOR, null, null, null, null,
                    utcNow().minusDays(random.nextInt(30)));
        }

        employeeRows.add(new Object[]{id, code, name, email, country[0], department[0], currency,
                salaryAt, onHold ? "ON_HOLD" : "ACTIVE", joined, deleted, 0});
    }

    /* ------------------------------------------------------------------ */

    private void audit(String entityType, long entityId, String action, String actor,
                       String changedFields, String refTable, Long refId, Long runId,
                       LocalDateTime createdAt) {
        auditRows.add(new Object[]{entityType, entityId, action, actor, changedFields,
                refTable, refId, runId, createdAt});
    }

    private void flushAll() {
        batch("""
                INSERT INTO employees (id, employee_code, name, email, country, department,
                    currency_code, annual_salary, status, joined_on, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, employeeRows);
        batch("""
                INSERT INTO salary_changes (id, employee_id, old_salary, new_salary, change_type,
                    percent_value, actor, bulk_raise_run_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, changeRows);
        batch("""
                INSERT INTO salary_credits (id, employee_id, year, month, amount, currency_code,
                    usd_rate, payroll_run_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, creditRows);
        batch("""
                INSERT INTO raise_review_items (id, employee_id, bulk_raise_run_id, proposed_old,
                    proposed_new, reason, status, resolved_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reviewRows);
        batch("""
                INSERT INTO audit_log (entity_type, entity_id, action, actor, changed_fields,
                    ref_table, ref_id, run_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, auditRows);
    }

    private void batch(String sql, List<Object[]> rows) {
        if (rows.isEmpty()) return;
        jdbc.batchUpdate(sql, rows);
        rows.clear();
    }

    private void finalisePayrollRuns(long[] ids, int[] counts, YearMonth firstMonth) {
        for (int m = 0; m < MONTHS; m++) {
            jdbc.update("UPDATE payroll_runs SET processed_count = ? WHERE id = ?", counts[m], ids[m]);
            audit("PAYROLL_RUN", ids[m], "RUN_COMPLETED", ACTOR, null, "payroll_runs", ids[m],
                    ids[m], runTime(firstMonth.plusMonths(m)).plusHours(1));
        }
        flushAll();
    }

    private void finaliseBulkRuns(BulkRun[] runs) {
        for (BulkRun run : runs) {
            jdbc.update("UPDATE bulk_raise_runs SET applied_count = ?, review_count = ? WHERE id = ?",
                    run.applied[0], run.review[0], run.id);
            LocalDateTime when = (LocalDateTime) jdbc.queryForObject(
                    "SELECT created_at FROM bulk_raise_runs WHERE id = ?", LocalDateTime.class, run.id);
            audit("BULK_RAISE_RUN", run.id, "RUN_COMPLETED", ACTOR, null, "bulk_raise_runs", run.id,
                    run.id, when.plusMinutes(30));
        }
        flushAll();
    }

    /* ------------------------------------------------------------------ */

    private Object[] pickWeighted(Object[][] options, int weightIndex) {
        int total = 0;
        for (Object[] o : options) total += (Integer) o[weightIndex];
        int roll = random.nextInt(total);
        for (Object[] o : options) {
            roll -= (Integer) o[weightIndex];
            if (roll < 0) return o;
        }
        return options[0];
    }

    private String rateOf(String currency) {
        for (Object[] c : CURRENCIES) {
            if (c[0].equals(currency)) return (String) c[2];
        }
        throw new IllegalStateException("Unknown currency " + currency);
    }

    /**
     * The containers (MySQL, backend) run on UTC; a seed run from a host JVM in
     * another zone must not write its local wall clock, or "recent" rows land in
     * the DB's future and pin the top of the created_at-ordered audit feed.
     */
    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Payroll runs happen on the 25th of each month at 06:00 UTC. */
    private LocalDateTime runTime(YearMonth ym) {
        return ym.atDay(25).atTime(6, 0);
    }

    /** Raises land on a random business-ish day of the month. */
    private LocalDateTime changeTime(YearMonth ym) {
        return ym.atDay(1 + random.nextInt(24)).atTime(9 + random.nextInt(8), random.nextInt(60));
    }

    private void logCounts(long started) {
        log.info("Seed complete in {}s — employees={}, changes={}, credits={}, audit={}",
                (System.currentTimeMillis() - started) / 1000,
                jdbc.queryForObject("SELECT COUNT(*) FROM employees", Long.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM salary_changes", Long.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM salary_credits", Long.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class));
    }

    private void shutdown() {
        // seed profile is a one-shot job: exit instead of serving traffic
        context.close();
    }
}

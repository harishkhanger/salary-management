package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.config.JobProperties;
import com.acme.salary.config.PayrollProperties;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.request.PayrollRunRequest;
import com.acme.salary.dto.MonthCreditAggregate;
import com.acme.salary.dto.response.PayrollMonthResponse;
import com.acme.salary.dto.response.PayrollRunResponse;
import com.acme.salary.dto.response.SalaryCreditResponse;
import com.acme.salary.entities.PayrollRun;
import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.enums.PayrollMonthState;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.events.AuditEvent;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.exception.ValidationException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.PayrollRunRepository;
import com.acme.salary.repository.SalaryCreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Payroll processing as a durable background job (same idiom as bulk raises).
 * Process-one and process-all share this single code path — a single-employee
 * run is just a cohort of size 1. Idempotency is check-then-insert: employees
 * already credited for the period are skipped and counted; the unique key
 * (employee_id, year, month) remains the referee for races. NOT @Transactional:
 * each credit commits alone via PayrollItemProcessor (REQUIRES_NEW).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {

    /** Upper bound for the month view: two years is more than any screen shows. */
    private static final int MAX_MONTHS_VIEW = 24;

    private final EmployeeRepository employeeRepository;
    private final SalaryCreditRepository salaryCreditRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemProcessor itemProcessor;
    private final PayrollProperties payrollProperties;
    private final JobProperties jobProperties;
    private final PaginationProperties paginationProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Persists the job record and returns immediately (202); the poller executes it. */
    public PayrollRunResponse queue(PayrollRunRequest request, String actor) {
        requireProcessablePeriod(request.year(), request.month());
        if (request.employeeId() != null) {
            employeeRepository.findByIdAndDeletedFalse(request.employeeId())
                    .orElseThrow(() -> new NotFoundException(
                            "Employee not found: " + request.employeeId()));
        }
        PayrollRun run = payrollRunRepository.save(PayrollRun.builder()
                .year(request.year())
                .month(request.month())
                .employeeId(request.employeeId())
                .initiatedBy(actor)
                .status(JobStatus.QUEUED)
                .createdAt(LocalDateTime.now(clock))
                .build());
        return PayrollRunResponse.from(run);
    }

    /**
     * Executes (or resumes) one run. Already-credited employees for the period
     * are derived from salary_credits — the idempotency key doubles as the
     * resume record, so re-entry after a crash never double-pays.
     */
    public void processRun(PayrollRun run) {
        run.setStatus(JobStatus.RUNNING);
        payrollRunRepository.save(run);

        List<Long> cohortIds = run.getEmployeeId() != null
                ? List.of(run.getEmployeeId())
                : employeeRepository.findCohortIds(null, null);
        Set<Long> held = new HashSet<>(employeeRepository.findHeldEmployeeIds());
        Set<Long> alreadyCredited = new HashSet<>(
                salaryCreditRepository.findEmployeeIdsCreditedForPeriod(run.getYear(), run.getMonth()));

        int processed = 0;
        int skippedHeld = 0;
        int alreadyProcessed = 0;
        int sinceProgressUpdate = 0;
        for (Long employeeId : cohortIds) {
            if (alreadyCredited.contains(employeeId)) {
                alreadyProcessed++;
                continue;
            }
            if (held.contains(employeeId)) {
                skippedHeld++;
                continue;
            }
            try {
                itemProcessor.credit(employeeId, run.getYear(), run.getMonth(), run.getId());
                processed++;
            } catch (RuntimeException e) {
                log.warn("Payroll run {} credit failed for employee {}: {}",
                        run.getId(), employeeId, e.getMessage());
            }
            if (++sinceProgressUpdate >= jobProperties.progressUpdateEvery()) {
                updateCounts(run, processed, skippedHeld, alreadyProcessed);
                sinceProgressUpdate = 0;
            }
        }

        updateCounts(run, processed, skippedHeld, alreadyProcessed);
        run.setStatus(JobStatus.COMPLETED);
        payrollRunRepository.save(run);
        eventPublisher.publishEvent(AuditEvent.builder()
                .entityType(AuditEntityType.PAYROLL_RUN).entityId(run.getId())
                .action(AuditAction.RUN_COMPLETED).actor(run.getInitiatedBy())
                .refTable("payroll_runs").refId(run.getId())
                .runId(run.getId()).build());
        log.info("Payroll run {} ({}-{}) completed: processed={}, skippedHeld={}, alreadyProcessed={}",
                run.getId(), run.getYear(), run.getMonth(), processed, skippedHeld, alreadyProcessed);
    }

    /**
     * The month-centric payroll screen: the current month and the N-1 before
     * it, newest first, each with the state that decides the button's label.
     * Counts come from aggregate queries; the 10k rows are never loaded.
     */
    public List<PayrollMonthResponse> months(int count) {
        int months = Math.max(1, Math.min(count, MAX_MONTHS_VIEW));
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        YearMonth oldest = current.minusMonths(months - 1L);

        Map<YearMonth, MonthCreditAggregate> credited = salaryCreditRepository
                .aggregateByPeriodSince(oldest.getYear() * 100 + oldest.getMonthValue()).stream()
                .collect(Collectors.toMap(a -> YearMonth.of(a.year(), a.month()), Function.identity()));
        Map<YearMonth, PayrollRun> inFlight = payrollRunRepository
                .findByStatusInAndEmployeeIdIsNull(List.of(JobStatus.QUEUED, JobStatus.RUNNING)).stream()
                .collect(Collectors.toMap(r -> YearMonth.of(r.getYear(), r.getMonth()),
                        Function.identity(), (a, b) -> a));
        long held = employeeRepository.countByDeletedFalseAndStatus(EmployeeStatus.ON_HOLD);

        List<PayrollMonthResponse> rows = new ArrayList<>();
        for (YearMonth ym = current; !ym.isBefore(oldest); ym = ym.minusMonths(1)) {
            MonthCreditAggregate agg = credited.get(ym);
            long creditedCount = agg == null ? 0 : agg.creditedCount();
            long unpaid = employeeRepository.countActiveUnpaidForPeriod(ym.getYear(), ym.getMonthValue());
            PayrollRun run = inFlight.get(ym);
            LocalDate opensOn = ym.atDay(payrollProperties.currentMonthProcessableFromDay());
            boolean opensLater = ym.equals(current) && today.isBefore(opensOn);

            PayrollMonthState state;
            if (run != null) {
                state = PayrollMonthState.PROCESSING;
            } else if (opensLater) {
                state = PayrollMonthState.OPENS_LATER;
            } else if (creditedCount == 0) {
                state = PayrollMonthState.DUE;
            } else if (unpaid == 0) {
                state = PayrollMonthState.PAID;
            } else {
                state = PayrollMonthState.PARTIAL;
            }
            rows.add(new PayrollMonthResponse(ym.getYear(), ym.getMonthValue(), state, creditedCount,
                    unpaid, held, agg == null ? null : agg.lastCreditedAt(),
                    opensLater ? opensOn : null, run == null ? null : run.getId()));
        }
        return rows;
    }

    public PayrollRunResponse getRun(Long id) {
        return PayrollRunResponse.from(payrollRunRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payroll run not found: " + id)));
    }

    public PageResponse<PayrollRunResponse> listRuns(int page, int size) {
        return PageResponse.from(payrollRunRepository.findAll(
                        PageRequest.of(paginationProperties.clampPage(page),
                                paginationProperties.clampSize(size),
                                Sort.by("createdAt").descending())),
                PayrollRunResponse::from);
    }

    public PageResponse<SalaryCreditResponse> creditHistory(Long employeeId, int page, int size) {
        employeeRepository.findByIdAndDeletedFalse(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + employeeId));
        return PageResponse.from(salaryCreditRepository.findHistory(employeeId,
                        PageRequest.of(paginationProperties.clampPage(page),
                                paginationProperties.clampSize(size))),
                SalaryCreditResponse::from);
    }

    /**
     * Per Harish: past months are always processable; the current month only
     * from the configured day (default the 25th); future months never.
     */
    private void requireProcessablePeriod(int year, int month) {
        YearMonth requested = YearMonth.of(year, month);
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        if (requested.isAfter(current)) {
            throw new ValidationException("Cannot process payroll for future month " + requested);
        }
        if (requested.equals(current)
                && today.getDayOfMonth() < payrollProperties.currentMonthProcessableFromDay()) {
            throw new ValidationException("Payroll for " + requested + " opens on day "
                    + payrollProperties.currentMonthProcessableFromDay() + " of the month");
        }
    }

    private void updateCounts(PayrollRun run, int processed, int skippedHeld, int alreadyProcessed) {
        run.setProcessedCount(processed);
        run.setSkippedHeldCount(skippedHeld);
        run.setAlreadyProcessedCount(alreadyProcessed);
        payrollRunRepository.save(run);
    }
}

package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.config.JobProperties;
import com.acme.salary.config.PayrollProperties;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.request.PayrollRunRequest;
import com.acme.salary.dto.response.PayrollRunResponse;
import com.acme.salary.dto.response.SalaryCreditResponse;
import com.acme.salary.entities.PayrollRun;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.exception.ValidationException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.PayrollRunRepository;
import com.acme.salary.repository.SalaryCreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private final EmployeeRepository employeeRepository;
    private final SalaryCreditRepository salaryCreditRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemProcessor itemProcessor;
    private final PayrollProperties payrollProperties;
    private final JobProperties jobProperties;
    private final PaginationProperties paginationProperties;
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
        log.info("Payroll run {} ({}-{}) completed: processed={}, skippedHeld={}, alreadyProcessed={}",
                run.getId(), run.getYear(), run.getMonth(), processed, skippedHeld, alreadyProcessed);
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

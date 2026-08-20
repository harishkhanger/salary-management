package com.acme.salary.scheduler;

import com.acme.salary.service.PayrollService;
import com.acme.salary.entities.PayrollRun;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.repository.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Relay for payroll jobs — same semantics as BulkRaiseRunPoller: RUNNING runs
 * are crashed jobs whose ledger-derived resume makes re-entry safe; the single
 * scheduler thread means ticks never overlap.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayrollRunPoller {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollService payrollService;

    @Scheduled(fixedDelayString = "${app.payroll.poll-interval-ms}")
    public void pickUpPendingRuns() {
        List<PayrollRun> pending = payrollRunRepository.findByStatusInOrderByIdAsc(
                List.of(JobStatus.QUEUED, JobStatus.RUNNING));
        for (PayrollRun run : pending) {
            log.info("Picking up payroll run {} ({})", run.getId(), run.getStatus());
            payrollService.processRun(run);
        }
    }
}

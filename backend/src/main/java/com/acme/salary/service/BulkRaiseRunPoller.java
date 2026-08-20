package com.acme.salary.service;

import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.enums.BulkRaiseStatus;
import com.acme.salary.repository.BulkRaiseRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Relay half of the outbox-style job: picks up QUEUED runs and drives them to
 * completion. RUNNING runs are included deliberately — after a crash they are
 * incomplete jobs, and the ledger-derived resume in processRun makes re-entry
 * safe. Spring's default single scheduler thread + fixedDelay means ticks
 * never overlap, so a run is only ever processed by one thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BulkRaiseRunPoller {

    private final BulkRaiseRunRepository bulkRaiseRunRepository;
    private final BulkRaiseService bulkRaiseService;

    @Scheduled(fixedDelayString = "${app.bulk-raise.poll-interval-ms}")
    public void pickUpPendingRuns() {
        List<BulkRaiseRun> pending = bulkRaiseRunRepository.findByStatusInOrderByIdAsc(
                List.of(BulkRaiseStatus.QUEUED, BulkRaiseStatus.RUNNING));
        for (BulkRaiseRun run : pending) {
            log.info("Picking up bulk raise run {} ({})", run.getId(), run.getStatus());
            bulkRaiseService.processRun(run);
        }
    }
}

package com.acme.salary.events;

import com.acme.salary.entities.AuditLog;
import com.acme.salary.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Writes the append-only audit ledger AFTER the business transaction commits:
 * an audit failure can never roll back a salary change, and rolled-back
 * transactions leave no phantom rows. Documented trade-off: a crash between
 * commit and this write loses the audit row (outbox is the production path).
 * fallbackExecution covers publishers that run outside a transaction (the
 * job orchestrators); REQUIRES_NEW because the source transaction is done.
 */
@Slf4j
@RequiredArgsConstructor
@org.springframework.stereotype.Component
public class AuditTrailListener {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .entityType(event.entityType().name())
                    .entityId(event.entityId())
                    .action(event.action().name())
                    .actor(event.actor())
                    .changedFields(event.changedFields() == null ? null
                            : objectMapper.writeValueAsString(event.changedFields()))
                    .refTable(event.refTable())
                    .refId(event.refId())
                    .runId(event.runId())
                    .createdAt(LocalDateTime.now(clock))
                    .build());
        } catch (RuntimeException e) {
            // never let audit failures propagate into request/job threads
            log.error("Audit write failed for {} {} on {}#{}: {}", event.action(),
                    event.refTable(), event.entityType(), event.entityId(), e.getMessage());
        }
    }
}

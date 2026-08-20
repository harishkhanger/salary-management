package com.acme.salary.events;

import com.acme.salary.entities.AuditLog;
import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import com.acme.salary.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditTrailListenerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditTrailListener listener() {
        return new AuditTrailListener(auditLogRepository, new ObjectMapper(), FIXED);
    }

    @Test
    void mapsEventToAppendOnlyRowWithJsonChangedFields() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        listener().record(AuditEvent.builder()
                .entityType(AuditEntityType.EMPLOYEE).entityId(7L)
                .action(AuditAction.PROFILE_UPDATED).actor("hr")
                .changedFields(Map.of("department", Map.of("old", "Sales", "new", "Platform")))
                .build());

        ArgumentCaptor<AuditLog> row = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(row.capture());
        assertThat(row.getValue().getEntityType()).isEqualTo("EMPLOYEE");
        assertThat(row.getValue().getEntityId()).isEqualTo(7L);
        assertThat(row.getValue().getAction()).isEqualTo("PROFILE_UPDATED");
        assertThat(row.getValue().getActor()).isEqualTo("hr");
        assertThat(row.getValue().getChangedFields()).contains("\"old\":\"Sales\"");
        assertThat(row.getValue().getCreatedAt()).isEqualTo(LocalDateTime.now(FIXED));
    }

    @Test
    void thinReferenceEventsCarryNoChangedFields() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        listener().record(AuditEvent.builder()
                .entityType(AuditEntityType.EMPLOYEE).entityId(7L)
                .action(AuditAction.SALARY_CHANGED).actor("hr")
                .refTable("salary_changes").refId(101L).runId(9L)
                .build());

        ArgumentCaptor<AuditLog> row = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(row.capture());
        assertThat(row.getValue().getChangedFields()).isNull();
        assertThat(row.getValue().getRefTable()).isEqualTo("salary_changes");
        assertThat(row.getValue().getRefId()).isEqualTo(101L);
        assertThat(row.getValue().getRunId()).isEqualTo(9L);
    }

    @Test
    void auditFailureNeverPropagates() {
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> listener().record(AuditEvent.builder()
                .entityType(AuditEntityType.EMPLOYEE).entityId(7L)
                .action(AuditAction.DELETED).actor("hr").build()))
                .doesNotThrowAnyException();
    }
}

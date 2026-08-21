package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.config.JobProperties;
import com.acme.salary.config.PayrollProperties;
import com.acme.salary.dto.request.PayrollRunRequest;
import com.acme.salary.dto.MonthCreditAggregate;
import com.acme.salary.dto.response.PayrollMonthResponse;
import com.acme.salary.dto.response.PayrollRunResponse;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.PayrollRun;
import com.acme.salary.entities.SalaryCredit;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.enums.PayrollMonthState;
import com.acme.salary.exception.ValidationException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.PayrollRunRepository;
import com.acme.salary.repository.SalaryCreditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    // Fixed "today": 2026-08-20 -> current month 2026-08, day 20 (before the 25th)
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalaryCreditRepository salaryCreditRepository;

    @Mock
    private PayrollRunRepository payrollRunRepository;

    @Mock
    private PayrollItemProcessor itemProcessor;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PayrollService service() {
        return new PayrollService(employeeRepository, salaryCreditRepository,
                payrollRunRepository, itemProcessor, new PayrollProperties(25),
                new JobProperties(100), new PaginationProperties(100), eventPublisher, FIXED);
    }

    private void stubRunSave() {
        when(payrollRunRepository.save(any(PayrollRun.class))).thenAnswer(inv -> {
            PayrollRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(11L);
            }
            return run;
        });
    }

    private PayrollRun queuedRun(Integer year, int month, Long employeeId) {
        return PayrollRun.builder()
                .id(11L).year(year).month(month).employeeId(employeeId)
                .initiatedBy("hr").status(JobStatus.QUEUED)
                .createdAt(LocalDateTime.now(FIXED))
                .build();
    }

    // --- month rule ---

    @Test
    void queueRejectsFutureMonth() {
        assertThatThrownBy(() -> service().queue(new PayrollRunRequest(2026, 9, null), "hr"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("future");
        verify(payrollRunRepository, never()).save(any());
    }

    @Test
    void queueRejectsCurrentMonthBeforeDay25() {
        assertThatThrownBy(() -> service().queue(new PayrollRunRequest(2026, 8, null), "hr"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("opens on day 25");
    }

    @Test
    void queueAllowsCurrentMonthOnOrAfterDay25() {
        Clock day25 = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        PayrollService service = new PayrollService(employeeRepository, salaryCreditRepository,
                payrollRunRepository, itemProcessor, new PayrollProperties(25),
                new JobProperties(100), new PaginationProperties(100), eventPublisher, day25);
        stubRunSave();

        PayrollRunResponse response = service.queue(new PayrollRunRequest(2026, 8, null), "hr");

        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void queueAllowsPastMonthAndDoesNotProcessInline() {
        stubRunSave();

        PayrollRunResponse response = service().queue(new PayrollRunRequest(2026, 7, null), "hr");

        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        verify(itemProcessor, never()).credit(anyLong(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void queueForSingleEmployeeValidatesExistence() {
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(new Employee()));
        stubRunSave();

        PayrollRunResponse response = service().queue(new PayrollRunRequest(2026, 7, 7L), "hr");

        assertThat(response.employeeId()).isEqualTo(7L);
    }

    // --- processRun ---

    @Test
    void processRunSkipsHeldAndAlreadyCreditedThenProcessesRest() {
        stubRunSave();
        PayrollRun run = queuedRun(2026, 7, null);
        when(employeeRepository.findCohortIds(null, null)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(employeeRepository.findHeldEmployeeIds()).thenReturn(List.of(2L));
        when(salaryCreditRepository.findEmployeeIdsCreditedForPeriod(2026, 7)).thenReturn(List.of(3L));
        when(itemProcessor.credit(anyLong(), eq(2026), eq(7), eq(11L)))
                .thenReturn(new SalaryCredit());

        service().processRun(run);

        assertThat(run.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(run.getProcessedCount()).isEqualTo(2);
        assertThat(run.getSkippedHeldCount()).isEqualTo(1);
        assertThat(run.getAlreadyProcessedCount()).isEqualTo(1);
        verify(itemProcessor, never()).credit(eq(2L), anyInt(), anyInt(), anyLong());
        verify(itemProcessor, never()).credit(eq(3L), anyInt(), anyInt(), anyLong());
    }

    @Test
    void processRunForSingleEmployeeUsesCohortOfOne() {
        stubRunSave();
        PayrollRun run = queuedRun(2026, 7, 7L);
        when(employeeRepository.findHeldEmployeeIds()).thenReturn(List.of());
        when(salaryCreditRepository.findEmployeeIdsCreditedForPeriod(2026, 7)).thenReturn(List.of());
        when(itemProcessor.credit(7L, 2026, 7, 11L)).thenReturn(new SalaryCredit());

        service().processRun(run);

        assertThat(run.getProcessedCount()).isEqualTo(1);
        verify(employeeRepository, never()).findCohortIds(any(), any());
    }

    @Test
    void oneFailingCreditDoesNotBlockTheRun() {
        stubRunSave();
        PayrollRun run = queuedRun(2026, 7, null);
        when(employeeRepository.findCohortIds(null, null)).thenReturn(List.of(1L, 2L, 3L));
        when(employeeRepository.findHeldEmployeeIds()).thenReturn(List.of());
        when(salaryCreditRepository.findEmployeeIdsCreditedForPeriod(2026, 7)).thenReturn(List.of());
        when(itemProcessor.credit(eq(1L), anyInt(), anyInt(), anyLong())).thenReturn(new SalaryCredit());
        when(itemProcessor.credit(eq(2L), anyInt(), anyInt(), anyLong()))
                .thenThrow(new RuntimeException("no rate for currency"));
        when(itemProcessor.credit(eq(3L), anyInt(), anyInt(), anyLong())).thenReturn(new SalaryCredit());

        service().processRun(run);

        assertThat(run.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(run.getProcessedCount()).isEqualTo(2);
    }

    // ---------- month-centric view ----------

    @Test
    void monthsDeriveStateFromCreditsUnpaidAndInFlightRuns() {
        // fixed today = 2026-08-20: August opens on the 25th; July fully paid;
        // June partially paid (12 joiners since); May never paid; April has a run in flight
        when(salaryCreditRepository.aggregateByPeriodSince(202604)).thenReturn(List.of(
                new MonthCreditAggregate(2026, 7, 10_000, LocalDateTime.of(2026, 7, 25, 6, 0)),
                new MonthCreditAggregate(2026, 6, 9_900, LocalDateTime.of(2026, 6, 25, 6, 0))));
        when(payrollRunRepository.findByStatusInAndEmployeeIdIsNull(any())).thenReturn(List.of(
                PayrollRun.builder().id(77L).year(2026).month(4).status(JobStatus.RUNNING).build()));
        when(employeeRepository.countByDeletedFalseAndStatus(EmployeeStatus.ON_HOLD)).thenReturn(4L);
        when(employeeRepository.countActiveUnpaidForPeriod(2026, 8)).thenReturn(9_700L);
        when(employeeRepository.countActiveUnpaidForPeriod(2026, 7)).thenReturn(0L);
        when(employeeRepository.countActiveUnpaidForPeriod(2026, 6)).thenReturn(12L);
        when(employeeRepository.countActiveUnpaidForPeriod(2026, 5)).thenReturn(9_700L);
        when(employeeRepository.countActiveUnpaidForPeriod(2026, 4)).thenReturn(500L);

        List<PayrollMonthResponse> rows = service().months(5);

        assertThat(rows).extracting(PayrollMonthResponse::month).containsExactly(8, 7, 6, 5, 4);
        assertThat(rows.get(0).state()).isEqualTo(PayrollMonthState.OPENS_LATER);
        assertThat(rows.get(0).opensOn()).isEqualTo(java.time.LocalDate.of(2026, 8, 25));
        assertThat(rows.get(1).state()).isEqualTo(PayrollMonthState.PAID);
        assertThat(rows.get(1).creditedCount()).isEqualTo(10_000);
        assertThat(rows.get(2).state()).isEqualTo(PayrollMonthState.PARTIAL);
        assertThat(rows.get(2).unpaidCount()).isEqualTo(12);
        assertThat(rows.get(3).state()).isEqualTo(PayrollMonthState.DUE);
        assertThat(rows.get(4).state()).isEqualTo(PayrollMonthState.PROCESSING);
        assertThat(rows.get(4).activeRunId()).isEqualTo(77L);
        assertThat(rows).allSatisfy(r -> assertThat(r.heldCount()).isEqualTo(4));
    }

    @Test
    void monthsWindowIsClampedToTheConfiguredMaximum() {
        when(salaryCreditRepository.aggregateByPeriodSince(anyInt())).thenReturn(List.of());
        when(payrollRunRepository.findByStatusInAndEmployeeIdIsNull(any())).thenReturn(List.of());
        when(employeeRepository.countActiveUnpaidForPeriod(anyInt(), anyInt())).thenReturn(0L);

        assertThat(service().months(500)).hasSize(24);
        assertThat(service().months(0)).hasSize(1);
    }
}

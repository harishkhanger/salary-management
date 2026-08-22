package com.acme.salary.service;

import com.acme.salary.config.JobProperties;
import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.request.BulkRaiseExecuteRequest;
import com.acme.salary.dto.request.BulkRaisePreviewRequest;
import com.acme.salary.dto.response.BulkRaisePreviewResponse;
import com.acme.salary.dto.response.BulkRaiseRunResponse;
import com.acme.salary.dto.CurrencyCohortAggregate;
import com.acme.salary.dto.OverThresholdAggregate;
import com.acme.salary.entities.OrgSettings;
import com.acme.salary.repository.OrgSettingsRepository;
import com.acme.salary.dto.response.SalaryChangeOutcome;
import com.acme.salary.entities.BulkRaiseRun;
import com.acme.salary.entities.CurrencyRate;
import com.acme.salary.enums.JobStatus;
import com.acme.salary.enums.RaiseType;
import com.acme.salary.repository.BulkRaiseRunRepository;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkRaiseServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalaryChangeRepository salaryChangeRepository;

    @Mock
    private RaiseReviewItemRepository raiseReviewItemRepository;

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @Mock
    private BulkRaiseRunRepository bulkRaiseRunRepository;

    @Mock
    private BulkRaiseItemProcessor itemProcessor;

    @Mock
    private OrgSettingsRepository orgSettingsRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private BulkRaiseService service;

    @BeforeEach
    void setUp() {
        service = new BulkRaiseService(employeeRepository, salaryChangeRepository,
                raiseReviewItemRepository, currencyRateRepository, bulkRaiseRunRepository,
                itemProcessor, orgSettingsRepository, new JobProperties(100),
                new PaginationProperties(100), new ObjectMapper(), eventPublisher, FIXED);
    }

    private CurrencyRate rate(String code, String usdRate) {
        CurrencyRate r = new CurrencyRate();
        r.setCode(code);
        r.setUsdRate(new BigDecimal(usdRate));
        return r;
    }

    private void stubRunSave() {
        when(bulkRaiseRunRepository.save(any(BulkRaiseRun.class))).thenAnswer(inv -> {
            BulkRaiseRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(9L);
            }
            return run;
        });
    }

    private BulkRaiseRun queuedRun(String excludedIdsJson) {
        return BulkRaiseRun.builder()
                .id(9L).raiseType(RaiseType.PERCENT).raiseValue(new BigDecimal("5"))
                .filterCountry("India").excludedIds(excludedIdsJson)
                .initiatedBy("hr").status(JobStatus.QUEUED)
                .createdAt(LocalDateTime.now(FIXED))
                .build();
    }

    private void stubThreshold(String percent) {
        OrgSettings settings = org.mockito.Mockito.mock(OrgSettings.class);
        when(settings.getRaiseThresholdPercent()).thenReturn(new BigDecimal(percent));
        when(orgSettingsRepository.findById(1L)).thenReturn(java.util.Optional.of(settings));
    }

    // --- preview ---

    @Test
    void previewComputesPerCurrencyImpactAndUsdDeltaFromAggregates() {
        when(employeeRepository.aggregateCohortByCurrency("India", null)).thenReturn(List.of(
                new CurrencyCohortAggregate("INR", 2L, new BigDecimal("2000000.00")),
                new CurrencyCohortAggregate("USD", 1L, new BigDecimal("100000.00"))));
        when(currencyRateRepository.findAll()).thenReturn(List.of(
                rate("INR", "80.000000"), rate("USD", "1.000000")));
        stubThreshold("30");
        when(salaryChangeRepository.findOverThreshold(eq("India"), isNull(), eq(new BigDecimal("30"))))
                .thenReturn(List.of(new OverThresholdAggregate(7L, "EMP-00007", "Asha Rao",
                        new BigDecimal("140000.00"), new BigDecimal("100000.00"),
                        LocalDateTime.now(FIXED).minusDays(30))));

        BulkRaisePreviewResponse preview = service.preview(
                new BulkRaisePreviewRequest(RaiseType.PERCENT, new BigDecimal("10"), "India", null, null));

        assertThat(preview.affectedCount()).isEqualTo(3);
        var inr = preview.costImpact().stream()
                .filter(c -> c.currencyCode().equals("INR")).findFirst().orElseThrow();
        assertThat(inr.proposed()).isEqualByComparingTo("2200000.00");
        assertThat(inr.delta()).isEqualByComparingTo("200000.00");
        // USD delta: 200000/80 + 10000/1 = 12500
        assertThat(preview.costImpactUsdDelta()).isEqualByComparingTo("12500.00");
        // flagged: 100k -> 140k since first change = +40% total, over the 30% threshold
        assertThat(preview.overThreshold()).hasSize(1);
        assertThat(preview.overThreshold().getFirst().totalRaisePercent()).isEqualByComparingTo("40.00");
    }

    @Test
    void previewWithFlatAmountAddsValuePerHead() {
        when(employeeRepository.aggregateCohortByCurrency(null, "Sales")).thenReturn(List.of(
                new CurrencyCohortAggregate("USD", 4L, new BigDecimal("400000.00"))));
        when(currencyRateRepository.findAll()).thenReturn(List.of(rate("USD", "1.000000")));
        stubThreshold("30");
        when(salaryChangeRepository.findOverThreshold(isNull(), eq("Sales"), any(BigDecimal.class)))
                .thenReturn(List.of());

        BulkRaisePreviewResponse preview = service.preview(
                new BulkRaisePreviewRequest(RaiseType.AMOUNT, new BigDecimal("5000"), null, "Sales", null));

        assertThat(preview.costImpact().getFirst().proposed()).isEqualByComparingTo("420000.00");
        assertThat(preview.costImpactUsdDelta()).isEqualByComparingTo("20000.00");
    }

    @Test
    void previewForHandPickedEmployeesUsesTheirAggregateAndIgnoresFilters() {
        when(employeeRepository.aggregateCohortByCurrencyIn(List.of(7L, 8L))).thenReturn(List.of(
                new CurrencyCohortAggregate("USD", 2L, new BigDecimal("200000.00"))));
        when(currencyRateRepository.findAll()).thenReturn(List.of(rate("USD", "1.000000")));
        stubThreshold("30");
        // flag list comes back org-wide and is narrowed to the picked ids
        when(salaryChangeRepository.findOverThreshold(isNull(), isNull(), any(BigDecimal.class)))
                .thenReturn(List.of(
                        new OverThresholdAggregate(7L, "EMP-00007", "Asha Rao", new BigDecimal("140000.00"),
                                new BigDecimal("100000.00"), LocalDateTime.now(FIXED)),
                        new OverThresholdAggregate(99L, "EMP-00099", "Someone Else", new BigDecimal("140000.00"),
                                new BigDecimal("100000.00"), LocalDateTime.now(FIXED))));

        BulkRaisePreviewResponse preview = service.preview(new BulkRaisePreviewRequest(
                RaiseType.PERCENT, new BigDecimal("10"), "India", "Sales", List.of(7L, 8L)));

        assertThat(preview.affectedCount()).isEqualTo(2);
        assertThat(preview.costImpactUsdDelta()).isEqualByComparingTo("20000.00");
        assertThat(preview.overThreshold()).extracting(o -> o.employeeId()).containsExactly(7L);
        verify(employeeRepository, never()).aggregateCohortByCurrency(any(), any());
    }

    @Test
    void processRunWithHandPickedCohortTouchesOnlyThoseEmployees() {
        stubRunSave();
        BulkRaiseRun run = queuedRun(null);
        run.setEmployeeIds("[5,6]");
        when(employeeRepository.findCohortIdsIn(List.of(5L, 6L))).thenReturn(List.of(5L, 6L));
        when(salaryChangeRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of());
        when(raiseReviewItemRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of());
        when(itemProcessor.process(eq(5L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));
        when(itemProcessor.process(eq(6L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));

        service.processRun(run);

        assertThat(run.getAppliedCount()).isEqualTo(2);
        verify(employeeRepository, never()).findCohortIds(any(), any());
    }

    // --- queue ---

    @Test
    void queuePersistsJobRecordAndDoesNotProcessAnything() {
        stubRunSave();

        BulkRaiseRunResponse response = service.queue(new BulkRaiseExecuteRequest(
                RaiseType.PERCENT, new BigDecimal("5"), "India", null, List.of(2L), null), "hr");

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(response.initiatedBy()).isEqualTo("hr");
        verify(itemProcessor, never()).process(any(), any(), any(), any(), any());
        verify(employeeRepository, never()).findCohortIds(any(), any());
    }

    // --- processRun ---

    @Test
    void processRunSkipsExcludedProcessesRestAndCompletes() {
        stubRunSave();
        BulkRaiseRun run = queuedRun("[2]");
        when(employeeRepository.findCohortIds("India", null)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(salaryChangeRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of());
        when(raiseReviewItemRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of());
        when(itemProcessor.process(eq(1L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));
        when(itemProcessor.process(eq(3L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.parked(55L, "over threshold"));
        when(itemProcessor.process(eq(4L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));

        service.processRun(run);

        assertThat(run.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(run.getAppliedCount()).isEqualTo(2);
        assertThat(run.getReviewCount()).isEqualTo(1);
        assertThat(run.getExcludedCount()).isEqualTo(1);
        verify(itemProcessor, never()).process(eq(2L), any(), any(), any(), any());
    }

    @Test
    void processRunResumesAfterCrashSkippingLedgerProcessedEmployees() {
        stubRunSave();
        BulkRaiseRun run = queuedRun(null);
        run.setStatus(JobStatus.RUNNING); // crashed mid-run
        when(employeeRepository.findCohortIds("India", null)).thenReturn(List.of(1L, 2L, 3L, 4L));
        // ledger says: 1 already applied, 2 already parked before the crash
        when(salaryChangeRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of(1L));
        when(raiseReviewItemRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of(2L));
        when(itemProcessor.process(eq(3L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));
        when(itemProcessor.process(eq(4L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));

        service.processRun(run);

        // 1 and 2 never reprocessed; counts include pre-crash work
        verify(itemProcessor, never()).process(eq(1L), any(), any(), any(), any());
        verify(itemProcessor, never()).process(eq(2L), any(), any(), any(), any());
        assertThat(run.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(run.getAppliedCount()).isEqualTo(3);
        assertThat(run.getReviewCount()).isEqualTo(1);
    }

    @Test
    void oneFailingItemDoesNotBlockTheBatch() {
        stubRunSave();
        BulkRaiseRun run = queuedRun(null);
        run.setFilterCountry(null);
        when(employeeRepository.findCohortIds(null, null)).thenReturn(List.of(1L, 2L, 3L));
        when(salaryChangeRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of());
        when(raiseReviewItemRepository.findEmployeeIdsByRun(9L)).thenReturn(List.of());
        when(itemProcessor.process(eq(1L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));
        when(itemProcessor.process(eq(2L), any(), any(), eq(9L), eq("hr")))
                .thenThrow(new RuntimeException("row lock timeout"));
        when(itemProcessor.process(eq(3L), any(), any(), eq(9L), eq("hr")))
                .thenReturn(SalaryChangeOutcome.applied(null, null));

        service.processRun(run);

        assertThat(run.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(run.getAppliedCount()).isEqualTo(2);
        assertThat(run.getReviewCount()).isEqualTo(0);
    }
}

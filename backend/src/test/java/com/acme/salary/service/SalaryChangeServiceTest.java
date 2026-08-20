package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.SalaryChangeOutcome;
import com.acme.salary.dto.SalaryChangeRequest;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.entities.SalaryChange;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.enums.ReviewStatus;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import com.acme.salary.service.strategy.FlatAmountRaise;
import com.acme.salary.service.strategy.PercentageRaise;
import com.acme.salary.service.strategy.SalaryCorrection;
import com.acme.salary.service.validation.RaiseValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryChangeServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalaryChangeRepository salaryChangeRepository;

    @Mock
    private RaiseReviewItemRepository raiseReviewItemRepository;

    @Mock
    private RaiseValidator alwaysPassValidator;

    private SalaryChangeService service;

    @BeforeEach
    void setUp() {
        service = new SalaryChangeService(employeeRepository, salaryChangeRepository,
                raiseReviewItemRepository,
                List.of(new PercentageRaise(), new FlatAmountRaise(), new SalaryCorrection()),
                List.of(alwaysPassValidator), new PaginationProperties(100), FIXED);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(7L);
        e.setEmployeeCode("EMP-00007");
        e.setName("Asha Rao");
        e.setEmail("asha@acme.test");
        e.setCountry("India");
        e.setDepartment("Engineering");
        e.setCurrencyCode("INR");
        e.setAnnualSalary(new BigDecimal("1000000.00"));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setJoinedOn(LocalDate.of(2024, 1, 1));
        e.setVersion(2);
        return e;
    }

    private void stubValidatorPass() {
        when(alwaysPassValidator.validate(any())).thenReturn(Optional.empty());
    }

    @Test
    void appliedPercentRaiseUpdatesSalaryAndAppendsChangeRow() {
        Employee employee = employee();
        stubValidatorPass();
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee));
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salaryChangeRepository.save(any(SalaryChange.class))).thenAnswer(inv -> {
            SalaryChange c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        SalaryChangeOutcome outcome = service.apply(7L,
                new SalaryChangeRequest(ChangeType.PERCENT, new BigDecimal("10"), 2), "hr");

        assertThat(outcome.status()).isEqualTo(SalaryChangeOutcome.Status.APPLIED);
        assertThat(employee.getAnnualSalary()).isEqualByComparingTo("1100000.00");

        ArgumentCaptor<SalaryChange> change = ArgumentCaptor.forClass(SalaryChange.class);
        verify(salaryChangeRepository).save(change.capture());
        assertThat(change.getValue().getOldSalary()).isEqualByComparingTo("1000000.00");
        assertThat(change.getValue().getNewSalary()).isEqualByComparingTo("1100000.00");
        assertThat(change.getValue().getChangeType()).isEqualTo(ChangeType.PERCENT);
        assertThat(change.getValue().getPercentValue()).isEqualByComparingTo("10");
        assertThat(change.getValue().getActor()).isEqualTo("hr");
        assertThat(change.getValue().getBulkRaiseRunId()).isNull();
    }

    @Test
    void parkedChangeCreatesReviewItemAndLeavesSalaryUntouched() {
        Employee employee = employee();
        when(alwaysPassValidator.validate(any())).thenReturn(Optional.of("over threshold"));
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee));
        when(raiseReviewItemRepository.save(any(RaiseReviewItem.class))).thenAnswer(inv -> {
            RaiseReviewItem item = inv.getArgument(0);
            item.setId(55L);
            return item;
        });

        SalaryChangeOutcome outcome = service.apply(7L,
                new SalaryChangeRequest(ChangeType.PERCENT, new BigDecimal("40"), 2), "hr");

        assertThat(outcome.status()).isEqualTo(SalaryChangeOutcome.Status.PARKED_FOR_REVIEW);
        assertThat(outcome.reviewItemId()).isEqualTo(55L);
        assertThat(outcome.reason()).isEqualTo("over threshold");
        assertThat(employee.getAnnualSalary()).isEqualByComparingTo("1000000.00");
        verify(employeeRepository, never()).saveAndFlush(any());
        verify(salaryChangeRepository, never()).save(any());

        ArgumentCaptor<RaiseReviewItem> item = ArgumentCaptor.forClass(RaiseReviewItem.class);
        verify(raiseReviewItemRepository).save(item.capture());
        assertThat(item.getValue().getProposedOld()).isEqualByComparingTo("1000000.00");
        assertThat(item.getValue().getProposedNew()).isEqualByComparingTo("1400000.00");
        assertThat(item.getValue().getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(item.getValue().getBulkRaiseRunId()).isNull();
    }

    @Test
    void correctionSetsAbsoluteSalaryDownward() {
        Employee employee = employee();
        stubValidatorPass();
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee));
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salaryChangeRepository.save(any(SalaryChange.class))).thenAnswer(inv -> inv.getArgument(0));

        service.apply(7L, new SalaryChangeRequest(ChangeType.CORRECTION, new BigDecimal("900000"), 2), "hr");

        assertThat(employee.getAnnualSalary()).isEqualByComparingTo("900000.00");
        ArgumentCaptor<SalaryChange> change = ArgumentCaptor.forClass(SalaryChange.class);
        verify(salaryChangeRepository).save(change.capture());
        assertThat(change.getValue().getPercentValue()).isNull();
    }

    @Test
    void staleVersionIsRejectedBeforeAnyWork() {
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee()));

        assertThatThrownBy(() -> service.apply(7L,
                new SalaryChangeRequest(ChangeType.PERCENT, new BigDecimal("10"), 1), "hr"))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("STALE_VERSION");
        verify(salaryChangeRepository, never()).save(any());
        verify(raiseReviewItemRepository, never()).save(any());
    }

    @Test
    void historyReturnsMappedPageNewestFirst() {
        SalaryChange change = new SalaryChange();
        change.setId(101L);
        change.setEmployeeId(7L);
        change.setOldSalary(new BigDecimal("1000000.00"));
        change.setNewSalary(new BigDecimal("1100000.00"));
        change.setChangeType(ChangeType.PERCENT);
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee()));
        when(salaryChangeRepository.findHistory(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(change), PageRequest.of(0, 20), 1));

        var page = service.history(7L, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().newSalary()).isEqualByComparingTo("1100000.00");
    }
}

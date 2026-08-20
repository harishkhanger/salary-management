package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.ReviewItemResponse;
import com.acme.salary.dto.SalaryChangeOutcome;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.entities.SalaryChange;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.enums.ReviewStatus;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RaiseReviewItemRepository raiseReviewItemRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SalaryChangeRepository salaryChangeRepository;

    private ReviewQueueService service;

    @BeforeEach
    void setUp() {
        service = new ReviewQueueService(raiseReviewItemRepository, employeeRepository,
                salaryChangeRepository, new PaginationProperties(100), FIXED);
    }

    private RaiseReviewItem pendingItem() {
        return RaiseReviewItem.builder()
                .id(55L).employeeId(7L).bulkRaiseRunId(9L)
                .proposedOld(new BigDecimal("1000000.00"))
                .proposedNew(new BigDecimal("1400000.00"))
                .reason("over threshold")
                .createdAt(LocalDateTime.now(FIXED).minusDays(1))
                .build();
    }

    private Employee employee(String salary) {
        Employee e = new Employee();
        e.setId(7L);
        e.setAnnualSalary(new BigDecimal(salary));
        e.setVersion(3);
        return e;
    }

    @Test
    void approveAppliesProposedSalaryAndResolvesItem() {
        RaiseReviewItem item = pendingItem();
        Employee employee = employee("1000000.00");
        when(raiseReviewItemRepository.findById(55L)).thenReturn(Optional.of(item));
        when(employeeRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(employee));
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(salaryChangeRepository.save(any(SalaryChange.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raiseReviewItemRepository.save(any(RaiseReviewItem.class))).thenAnswer(inv -> inv.getArgument(0));

        SalaryChangeOutcome outcome = service.approve(55L, "hr");

        assertThat(outcome.status()).isEqualTo(SalaryChangeOutcome.Status.APPLIED);
        assertThat(employee.getAnnualSalary()).isEqualByComparingTo("1400000.00");
        assertThat(item.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(item.getResolvedAt()).isNotNull();

        ArgumentCaptor<SalaryChange> change = ArgumentCaptor.forClass(SalaryChange.class);
        verify(salaryChangeRepository).save(change.capture());
        assertThat(change.getValue().getChangeType()).isEqualTo(ChangeType.CORRECTION);
        assertThat(change.getValue().getBulkRaiseRunId()).isEqualTo(9L);
        assertThat(change.getValue().getActor()).isEqualTo("hr");
    }

    @Test
    void approveRejectsWhenSalaryChangedSinceParking() {
        when(raiseReviewItemRepository.findById(55L)).thenReturn(Optional.of(pendingItem()));
        when(employeeRepository.findByIdAndDeletedFalse(7L))
                .thenReturn(Optional.of(employee("1100000.00")));

        assertThatThrownBy(() -> service.approve(55L, "hr"))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("STALE_PROPOSAL");
        verify(salaryChangeRepository, never()).save(any());
    }

    @Test
    void approveRejectsAlreadyResolvedItem() {
        RaiseReviewItem resolved = pendingItem();
        resolved.setStatus(ReviewStatus.REJECTED);
        when(raiseReviewItemRepository.findById(55L)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> service.approve(55L, "hr"))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ALREADY_RESOLVED");
    }

    @Test
    void rejectMarksItemRejectedWithoutTouchingSalary() {
        RaiseReviewItem item = pendingItem();
        when(raiseReviewItemRepository.findById(55L)).thenReturn(Optional.of(item));
        when(raiseReviewItemRepository.save(any(RaiseReviewItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewItemResponse response = service.reject(55L);

        assertThat(response.status()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(item.getResolvedAt()).isNotNull();
        verify(employeeRepository, never()).saveAndFlush(any());
        verify(salaryChangeRepository, never()).save(any());
    }
}

package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.response.EmployeeResponse;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.dto.response.ReviewItemResponse;
import com.acme.salary.dto.response.SalaryChangeOutcome;
import com.acme.salary.dto.response.SalaryChangeResponse;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.enums.AuditAction;
import com.acme.salary.enums.AuditEntityType;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.events.AuditEvent;
import com.acme.salary.enums.ReviewStatus;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private final RaiseReviewItemRepository raiseReviewItemRepository;
    private final EmployeeRepository employeeRepository;
    private final RaiseExecutor raiseExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final PaginationProperties paginationProperties;
    private final Clock clock;

    /** Queue listing, oldest first, enriched with employee code/name. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewItemResponse> list(ReviewStatus status, int page, int size) {
        org.springframework.data.domain.PageRequest pageRequest =
                org.springframework.data.domain.PageRequest.of(
                        paginationProperties.clampPage(page), paginationProperties.clampSize(size),
                        org.springframework.data.domain.Sort.by("createdAt").ascending());
        org.springframework.data.domain.Page<RaiseReviewItem> items = status == null
                ? raiseReviewItemRepository.findAll(pageRequest)
                : raiseReviewItemRepository.findByStatus(status, pageRequest);
        java.util.Map<Long, Employee> employeesById = employeeRepository
                .findAllById(items.getContent().stream().map(RaiseReviewItem::getEmployeeId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(Employee::getId, e -> e));
        return PageResponse.from(items,
                item -> ReviewItemResponse.from(item, employeesById.get(item.getEmployeeId())));
    }

    /**
     * Applies the parked proposal. Validators are deliberately skipped — a
     * human has already reviewed; but the proposal must still match reality:
     * if the salary moved since parking, the proposal is stale.
     */
    @Transactional
    public SalaryChangeOutcome approve(Long itemId, String actor) {
        RaiseReviewItem item = findPending(itemId);
        Employee employee = employeeRepository.findByIdAndDeletedFalse(item.getEmployeeId())
                .orElseThrow(() -> new NotFoundException(
                        "Employee not found: " + item.getEmployeeId()));
        if (employee.getAnnualSalary().compareTo(item.getProposedOld()) != 0) {
            throw new ConflictException("STALE_PROPOSAL",
                    "Salary changed since this proposal was parked (" + item.getProposedOld()
                            + " -> " + employee.getAnnualSalary() + "); reject and re-raise");
        }

        // one code path: RaiseExecutor.apply skips validators (a human decided)
        // and publishes the SALARY_CHANGED audit event itself
        SalaryChangeOutcome outcome = raiseExecutor.apply(employee, item.getProposedNew(),
                ChangeType.CORRECTION, null, actor, item.getBulkRaiseRunId());
        resolve(item, ReviewStatus.APPROVED);
        eventPublisher.publishEvent(AuditEvent.builder()
                .entityType(AuditEntityType.EMPLOYEE).entityId(employee.getId())
                .action(AuditAction.RAISE_APPROVED).actor(actor)
                .refTable("raise_review_items").refId(item.getId())
                .runId(item.getBulkRaiseRunId()).build());
        return outcome;
    }

    @Transactional
    public ReviewItemResponse reject(Long itemId, String actor) {
        RaiseReviewItem item = findPending(itemId);
        resolve(item, ReviewStatus.REJECTED);
        eventPublisher.publishEvent(AuditEvent.builder()
                .entityType(AuditEntityType.EMPLOYEE).entityId(item.getEmployeeId())
                .action(AuditAction.RAISE_REJECTED).actor(actor)
                .refTable("raise_review_items").refId(item.getId())
                .runId(item.getBulkRaiseRunId()).build());
        return ReviewItemResponse.from(item, null);
    }

    private RaiseReviewItem findPending(Long itemId) {
        RaiseReviewItem item = raiseReviewItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Review item not found: " + itemId));
        if (item.getStatus() != ReviewStatus.PENDING) {
            throw new ConflictException("ALREADY_RESOLVED",
                    "Review item " + itemId + " is already " + item.getStatus());
        }
        return item;
    }

    private void resolve(RaiseReviewItem item, ReviewStatus status) {
        item.setStatus(status);
        item.setResolvedAt(LocalDateTime.now(clock));
        raiseReviewItemRepository.save(item);
    }
}

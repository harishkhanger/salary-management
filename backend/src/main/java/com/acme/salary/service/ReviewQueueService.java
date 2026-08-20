package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.EmployeeResponse;
import com.acme.salary.dto.PageResponse;
import com.acme.salary.dto.ReviewItemResponse;
import com.acme.salary.dto.SalaryChangeOutcome;
import com.acme.salary.dto.SalaryChangeResponse;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.entities.SalaryChange;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.enums.ReviewStatus;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private final RaiseReviewItemRepository raiseReviewItemRepository;
    private final EmployeeRepository employeeRepository;
    private final SalaryChangeRepository salaryChangeRepository;
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

        employee.setAnnualSalary(item.getProposedNew());
        Employee saved = employeeRepository.saveAndFlush(employee);
        SalaryChange change = salaryChangeRepository.save(SalaryChange.builder()
                .employeeId(employee.getId())
                .oldSalary(item.getProposedOld())
                .newSalary(item.getProposedNew())
                .changeType(ChangeType.CORRECTION)
                .actor(actor)
                .bulkRaiseRunId(item.getBulkRaiseRunId())
                .createdAt(LocalDateTime.now(clock))
                .build());
        resolve(item, ReviewStatus.APPROVED);
        return SalaryChangeOutcome.applied(SalaryChangeResponse.from(change),
                EmployeeResponse.from(saved));
    }

    @Transactional
    public ReviewItemResponse reject(Long itemId) {
        RaiseReviewItem item = findPending(itemId);
        resolve(item, ReviewStatus.REJECTED);
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

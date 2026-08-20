package com.acme.salary.service;

import com.acme.salary.dto.response.EmployeeResponse;
import com.acme.salary.dto.response.SalaryChangeOutcome;
import com.acme.salary.dto.response.SalaryChangeResponse;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.entities.SalaryChange;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import com.acme.salary.service.strategy.RaiseCalculation;
import com.acme.salary.service.validation.RaiseContext;
import com.acme.salary.service.validation.RaiseValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single code path every salary mutation goes through — individual
 * changes, bulk-raise items, and review-queue approvals all end here.
 * Participates in the caller's transaction (no @Transactional of its own).
 */
@Component
public class RaiseExecutor {

    private final EmployeeRepository employeeRepository;
    private final SalaryChangeRepository salaryChangeRepository;
    private final RaiseReviewItemRepository raiseReviewItemRepository;
    private final Map<ChangeType, RaiseCalculation> calculations;
    private final List<RaiseValidator> validators;
    private final Clock clock;

    public RaiseExecutor(EmployeeRepository employeeRepository,
                         SalaryChangeRepository salaryChangeRepository,
                         RaiseReviewItemRepository raiseReviewItemRepository,
                         List<RaiseCalculation> calculations,
                         List<RaiseValidator> validators,
                         Clock clock) {
        this.employeeRepository = employeeRepository;
        this.salaryChangeRepository = salaryChangeRepository;
        this.raiseReviewItemRepository = raiseReviewItemRepository;
        this.calculations = new EnumMap<>(ChangeType.class);
        calculations.forEach(c -> this.calculations.put(c.type(), c));
        this.validators = validators;
        this.clock = clock;
    }

    /**
     * Computes the proposed salary, runs the validator pipeline, then either
     * applies the change or parks it for review. bulkRaiseRunId is null for
     * individual changes.
     */
    public SalaryChangeOutcome execute(Employee employee, ChangeType changeType,
                                       BigDecimal value, String actor, Long bulkRaiseRunId) {
        BigDecimal currentSalary = employee.getAnnualSalary();
        BigDecimal proposedSalary = calculations.get(changeType).newSalary(currentSalary, value);
        BigDecimal percentValue = changeType == ChangeType.PERCENT ? value : null;

        RaiseContext context = new RaiseContext(employee, currentSalary, proposedSalary,
                changeType, percentValue);
        Optional<String> parkReason = firstParkReason(context);
        if (parkReason.isPresent()) {
            RaiseReviewItem item = raiseReviewItemRepository.save(RaiseReviewItem.builder()
                    .employeeId(employee.getId())
                    .bulkRaiseRunId(bulkRaiseRunId)
                    .proposedOld(currentSalary)
                    .proposedNew(proposedSalary)
                    .reason(parkReason.get())
                    .createdAt(LocalDateTime.now(clock))
                    .build());
            return SalaryChangeOutcome.parked(item.getId(), parkReason.get());
        }
        return apply(employee, proposedSalary, changeType, percentValue, actor, bulkRaiseRunId);
    }

    /**
     * Applies a salary directly, bypassing the validator pipeline. Used by
     * review-queue approval, where a human has already decided.
     */
    public SalaryChangeOutcome apply(Employee employee, BigDecimal newSalary,
                                     ChangeType changeType, BigDecimal percentValue,
                                     String actor, Long bulkRaiseRunId) {
        BigDecimal oldSalary = employee.getAnnualSalary();
        employee.setAnnualSalary(newSalary);
        Employee saved = employeeRepository.saveAndFlush(employee);
        SalaryChange change = salaryChangeRepository.save(SalaryChange.builder()
                .employeeId(employee.getId())
                .oldSalary(oldSalary)
                .newSalary(newSalary)
                .changeType(changeType)
                .percentValue(percentValue)
                .actor(actor)
                .bulkRaiseRunId(bulkRaiseRunId)
                .createdAt(LocalDateTime.now(clock))
                .build());
        return SalaryChangeOutcome.applied(SalaryChangeResponse.from(change),
                EmployeeResponse.from(saved));
    }

    /**
     * Runs the validator pipeline in @Order sequence. The first validator
     * returning a reason decides: the change is parked and later validators
     * never run (so their queries never execute).
     */
    private Optional<String> firstParkReason(RaiseContext context) {
        for (RaiseValidator validator : validators) {
            Optional<String> reason = validator.validate(context);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }
}

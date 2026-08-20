package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.EmployeeResponse;
import com.acme.salary.dto.PageResponse;
import com.acme.salary.dto.SalaryChangeOutcome;
import com.acme.salary.dto.SalaryChangeRequest;
import com.acme.salary.dto.SalaryChangeResponse;
import com.acme.salary.entities.Employee;
import com.acme.salary.entities.RaiseReviewItem;
import com.acme.salary.entities.SalaryChange;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.RaiseReviewItemRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import com.acme.salary.service.strategy.RaiseCalculation;
import com.acme.salary.service.validation.RaiseContext;
import com.acme.salary.service.validation.RaiseValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SalaryChangeService {

    private final EmployeeRepository employeeRepository;
    private final SalaryChangeRepository salaryChangeRepository;
    private final RaiseReviewItemRepository raiseReviewItemRepository;
    private final Map<ChangeType, RaiseCalculation> calculations;
    private final List<RaiseValidator> validators;
    private final PaginationProperties paginationProperties;
    private final Clock clock;

    public SalaryChangeService(EmployeeRepository employeeRepository,
                               SalaryChangeRepository salaryChangeRepository,
                               RaiseReviewItemRepository raiseReviewItemRepository,
                               List<RaiseCalculation> calculations,
                               List<RaiseValidator> validators,
                               PaginationProperties paginationProperties,
                               Clock clock) {
        this.employeeRepository = employeeRepository;
        this.salaryChangeRepository = salaryChangeRepository;
        this.raiseReviewItemRepository = raiseReviewItemRepository;
        this.calculations = new EnumMap<>(ChangeType.class);
        calculations.forEach(c -> this.calculations.put(c.type(), c));
        this.validators = validators;
        this.paginationProperties = paginationProperties;
        this.clock = clock;
    }

    @Transactional
    public SalaryChangeOutcome apply(Long employeeId, SalaryChangeRequest request, String actor) {
        Employee employee = findActive(employeeId);
        if (employee.getVersion() != request.version()) {
            throw new ConflictException("STALE_VERSION", "Stale version " + request.version()
                    + " — the record was modified since it was loaded; reload and retry");
        }

        BigDecimal currentSalary = employee.getAnnualSalary();
        BigDecimal proposedSalary = calculations.get(request.changeType())
                .newSalary(currentSalary, request.value());
        BigDecimal percentValue = request.changeType() == ChangeType.PERCENT ? request.value() : null;

        RaiseContext context = new RaiseContext(employee, currentSalary, proposedSalary,
                request.changeType(), percentValue);
        Optional<String> parkReason = firstParkReason(context);

        if (parkReason.isPresent()) {
            RaiseReviewItem item = raiseReviewItemRepository.save(RaiseReviewItem.builder()
                    .employeeId(employeeId)
                    .proposedOld(currentSalary)
                    .proposedNew(proposedSalary)
                    .reason(parkReason.get())
                    .createdAt(LocalDateTime.now(clock))
                    .build());
            return SalaryChangeOutcome.parked(item.getId(), parkReason.get());
        }

        employee.setAnnualSalary(proposedSalary);
        Employee saved = employeeRepository.saveAndFlush(employee);
        SalaryChange change = salaryChangeRepository.save(SalaryChange.builder()
                .employeeId(employeeId)
                .oldSalary(currentSalary)
                .newSalary(proposedSalary)
                .changeType(request.changeType())
                .percentValue(percentValue)
                .actor(actor)
                .createdAt(LocalDateTime.now(clock))
                .build());
        return SalaryChangeOutcome.applied(SalaryChangeResponse.from(change),
                EmployeeResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public PageResponse<SalaryChangeResponse> history(Long employeeId, int page, int size) {
        findActive(employeeId);
        return PageResponse.from(
                salaryChangeRepository.findHistory(employeeId,
                        PageRequest.of(paginationProperties.clampPage(page),
                                paginationProperties.clampSize(size))),
                SalaryChangeResponse::from);
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

    private Employee findActive(Long id) {
        return employeeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }
}

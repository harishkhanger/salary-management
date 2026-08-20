package com.acme.salary.service;

import com.acme.salary.dto.response.SalaryChangeOutcome;
import com.acme.salary.entities.Employee;
import com.acme.salary.enums.ChangeType;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * One bulk-raise item = one transaction. Deliberately a SEPARATE bean from
 * BulkRaiseService: REQUIRES_NEW only takes effect through the Spring proxy,
 * and self-invocation would bypass it. A failing item rolls back alone and
 * never blocks the rest of the batch.
 */
@Component
@RequiredArgsConstructor
public class BulkRaiseItemProcessor {

    private final EmployeeRepository employeeRepository;
    private final RaiseExecutor raiseExecutor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SalaryChangeOutcome process(Long employeeId, ChangeType changeType,
                                       BigDecimal value, Long runId, String actor) {
        Employee employee = employeeRepository.findByIdAndDeletedFalse(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + employeeId));
        return raiseExecutor.execute(employee, changeType, value, actor, runId);
    }
}

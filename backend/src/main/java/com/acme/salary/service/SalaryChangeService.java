package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.PageResponse;
import com.acme.salary.dto.SalaryChangeOutcome;
import com.acme.salary.dto.SalaryChangeRequest;
import com.acme.salary.dto.SalaryChangeResponse;
import com.acme.salary.entities.Employee;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.EmployeeRepository;
import com.acme.salary.repository.SalaryChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalaryChangeService {

    private final EmployeeRepository employeeRepository;
    private final SalaryChangeRepository salaryChangeRepository;
    private final RaiseExecutor raiseExecutor;
    private final PaginationProperties paginationProperties;

    @Transactional
    public SalaryChangeOutcome apply(Long employeeId, SalaryChangeRequest request, String actor) {
        Employee employee = findActive(employeeId);
        if (employee.getVersion() != request.version()) {
            throw new ConflictException("STALE_VERSION", "Stale version " + request.version()
                    + " — the record was modified since it was loaded; reload and retry");
        }
        return raiseExecutor.execute(employee, request.changeType(), request.value(), actor, null);
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

    private Employee findActive(Long id) {
        return employeeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }
}

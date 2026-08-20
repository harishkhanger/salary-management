package com.acme.salary.service;

import com.acme.salary.config.PaginationProperties;
import com.acme.salary.dto.request.EmployeeCreateRequest;
import com.acme.salary.dto.request.EmployeeStatusRequest;
import com.acme.salary.dto.response.EmployeeResponse;
import com.acme.salary.dto.request.EmployeeUpdateRequest;
import com.acme.salary.dto.response.PageResponse;
import com.acme.salary.entities.Employee;
import com.acme.salary.enums.EmployeeStatus;
import com.acme.salary.exception.ConflictException;
import com.acme.salary.exception.NotFoundException;
import com.acme.salary.repository.CurrencyRateRepository;
import com.acme.salary.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final PaginationProperties paginationProperties;

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        requireKnownCurrency(request.currencyCode());
        boolean codeSupplied = request.employeeCode() != null && !request.employeeCode().isBlank();
        if (codeSupplied && employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException("DUPLICATE_CODE",
                    "Employee code already in use: " + request.employeeCode());
        }

        Employee employee = Employee.builder()
                // placeholder satisfies NOT NULL until the id-derived code is assigned below
                .employeeCode(codeSupplied ? request.employeeCode() : "PENDING")
                .name(request.name())
                .email(request.email())
                .country(request.country())
                .department(request.department())
                .currencyCode(request.currencyCode())
                .annualSalary(request.annualSalary())
                .status(EmployeeStatus.ACTIVE)
                .joinedOn(request.joinedOn())
                .build();

        Employee saved = employeeRepository.save(employee);
        if (!codeSupplied) {
            // assigning the id-derived code is a second UPDATE that bumps the
            // optimistic-lock version; flush so the response carries the real one
            saved.setEmployeeCode("EMP-%05d".formatted(saved.getId()));
            saved = employeeRepository.saveAndFlush(saved);
        }
        return EmployeeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        return EmployeeResponse.from(findActive(id));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = findActive(id);
        if (employee.getVersion() != request.version()) {
            throw new ConflictException("STALE_VERSION", "Stale version " + request.version()
                    + " — the record was modified since it was loaded; reload and retry");
        }
        requireKnownCurrency(request.currencyCode());

        // toBuilder copies id, version, and the fields this flow must not touch
        // (salary, status, code); salary changes and holds have their own flows
        Employee updated = employee.toBuilder()
                .name(request.name())
                .email(request.email())
                .country(request.country())
                .department(request.department())
                .currencyCode(request.currencyCode())
                .joinedOn(request.joinedOn())
                .build();
        // flush so the response carries the post-update optimistic-lock version
        return EmployeeResponse.from(employeeRepository.saveAndFlush(updated));
    }

    /** Salary hold / release. Holds block payout, never compensation changes. */
    @Transactional
    public EmployeeResponse changeStatus(Long id, EmployeeStatusRequest request) {
        Employee employee = findActive(id);
        if (employee.getVersion() != request.version()) {
            throw new ConflictException("STALE_VERSION", "Stale version " + request.version()
                    + " — the record was modified since it was loaded; reload and retry");
        }
        employee.setStatus(request.status());
        // flush so the response carries the post-update optimistic-lock version
        return EmployeeResponse.from(employeeRepository.saveAndFlush(employee));
    }

    @Transactional
    public void softDelete(Long id) {
        Employee employee = findActive(id);
        employee.setDeleted(true);
        employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> directory(int page, int size, String search,
                                                    String country, String department,
                                                    EmployeeStatus status) {
        Page<Employee> result = employeeRepository.findAll(
                directorySpec(search, country, department, status),
                PageRequest.of(paginationProperties.clampPage(page),
                        paginationProperties.clampSize(size), Sort.by("name").ascending()));
        return PageResponse.from(result, EmployeeResponse::from);
    }

    private Specification<Employee> directorySpec(String search, String country,
                                                  String department, EmployeeStatus status) {
        Specification<Employee> spec = (root, q, cb) -> cb.isFalse(root.get("deleted"));
        if (country != null && !country.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("country"), country));
        }
        if (department != null && !department.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("department"), department));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), term),
                    cb.like(cb.lower(root.get("employeeCode")), term)));
        }
        return spec;
    }

    private Employee findActive(Long id) {
        return employeeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }

    private void requireKnownCurrency(String currencyCode) {
        if (!currencyRateRepository.existsById(currencyCode)) {
            throw new ConflictException("UNKNOWN_CURRENCY", "Unknown currency: " + currencyCode);
        }
    }
}

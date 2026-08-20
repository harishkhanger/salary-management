package com.acme.salary.dto.response;

import com.acme.salary.entities.Employee;
import com.acme.salary.enums.EmployeeStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String name,
        String email,
        String country,
        String department,
        String currencyCode,
        BigDecimal annualSalary,
        EmployeeStatus status,
        LocalDate joinedOn,
        int version
) {
    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(e.getId(), e.getEmployeeCode(), e.getName(), e.getEmail(),
                e.getCountry(), e.getDepartment(), e.getCurrencyCode(), e.getAnnualSalary(),
                e.getStatus(), e.getJoinedOn(), e.getVersion());
    }
}

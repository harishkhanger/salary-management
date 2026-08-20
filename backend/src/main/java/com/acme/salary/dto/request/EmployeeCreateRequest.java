package com.acme.salary.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeCreateRequest(
        @Size(max = 20) @Pattern(regexp = "[A-Z0-9-]*", message = "uppercase letters, digits and dashes only")
        String employeeCode,

        @NotBlank @Size(max = 100)
        String name,

        @NotBlank @Email @Size(max = 150)
        String email,

        @NotBlank @Size(max = 60)
        String country,

        @NotBlank @Size(max = 60)
        String department,

        @NotBlank @Size(min = 3, max = 3)
        String currencyCode,

        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2)
        BigDecimal annualSalary,

        @NotNull
        LocalDate joinedOn
) {
}

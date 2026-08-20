package com.acme.salary.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Profile-only update: salary changes and holds have their own flows and are
 * deliberately absent here. The version field implements optimistic locking
 * end-to-end — a stale client update is rejected with 409.
 */
public record EmployeeUpdateRequest(
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

        @NotNull
        LocalDate joinedOn,

        @NotNull
        Integer version
) {
}

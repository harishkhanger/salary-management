package com.acme.salary.dto;

import java.time.LocalDateTime;

/** Flagged in the preview so HR can optionally exclude them from the run. */
public record RecentlyRaisedEmployee(Long employeeId, String employeeCode, String name,
                                     LocalDateTime lastRaiseAt) {
}

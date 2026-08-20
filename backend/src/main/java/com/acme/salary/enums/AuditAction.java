package com.acme.salary.enums;

public enum AuditAction {
    // employee lifecycle — changed_fields JSON carries old -> new
    CREATED,
    PROFILE_UPDATED,
    STATUS_CHANGED,
    DELETED,
    // money events — thin references to the owning row, no amounts duplicated
    SALARY_CHANGED,
    RAISE_PARKED,
    RAISE_APPROVED,
    RAISE_REJECTED,
    SALARY_CREDITED,
    // collapsed header row for the global feed (approach b)
    RUN_COMPLETED,
    // settings surface
    RATE_UPDATED,
    THRESHOLD_UPDATED
}

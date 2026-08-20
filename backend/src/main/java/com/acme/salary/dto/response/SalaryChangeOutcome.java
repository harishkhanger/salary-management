package com.acme.salary.dto.response;

/**
 * A change request has two legitimate outcomes, both 200: APPLIED (salary
 * updated, change row appended) or PARKED_FOR_REVIEW (guardrail tripped,
 * review item created, salary untouched).
 */
public record SalaryChangeOutcome(
        Status status,
        SalaryChangeResponse change,
        EmployeeResponse employee,
        Long reviewItemId,
        String reason
) {
    public enum Status { APPLIED, PARKED_FOR_REVIEW }

    public static SalaryChangeOutcome applied(SalaryChangeResponse change, EmployeeResponse employee) {
        return new SalaryChangeOutcome(Status.APPLIED, change, employee, null, null);
    }

    public static SalaryChangeOutcome parked(Long reviewItemId, String reason) {
        return new SalaryChangeOutcome(Status.PARKED_FOR_REVIEW, null, null, reviewItemId, reason);
    }
}

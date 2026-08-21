package com.acme.salary.enums;

/** What the payroll screen says about a month — the state drives the button's label. */
public enum PayrollMonthState {
    /** Current month before the processable day. */
    OPENS_LATER,
    /** Nobody credited yet; the whole org can be paid. */
    DUE,
    /** A run for this month is queued or running right now. */
    PROCESSING,
    /** Some active employees credited, some still unpaid (joiners, released holds). */
    PARTIAL,
    /** Every active employee credited. */
    PAID
}

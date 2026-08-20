-- V3: payroll runs join the durable-job idiom (same as bulk raises).
-- status drives the poller; employee_id records single-employee runs so a
-- crashed run knows its target. Which employees are already credited is
-- DERIVED from salary_credits (the unique key doubles as the resume record).

ALTER TABLE payroll_runs ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE payroll_runs ADD COLUMN employee_id BIGINT NULL;
ALTER TABLE payroll_runs ADD CONSTRAINT chk_payroll_run_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED'));
ALTER TABLE payroll_runs ADD CONSTRAINT fk_payroll_run_employee
    FOREIGN KEY (employee_id) REFERENCES employees (id);

-- V6: the day of the month from which the CURRENT month's payroll may be
-- processed moves from application config into org_settings, so HR can change
-- it from the Settings page and the change is audited like the raise guardrail.
-- Capped at 28 so the rule holds in February.

ALTER TABLE org_settings ADD COLUMN payroll_day INT NOT NULL DEFAULT 25;
ALTER TABLE org_settings ADD CONSTRAINT chk_org_settings_payroll_day CHECK (payroll_day BETWEEN 1 AND 28);

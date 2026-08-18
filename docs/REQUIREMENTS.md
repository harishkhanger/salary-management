# Salary Management System — Requirements

## Goal

ACME's HR team manages salary data for ~10,000 employees across multiple countries entirely in Excel — error-prone, slow, and unable to answer basic questions about how the organization pays people. This project replaces that workflow with a web application through which a single HR manager can manage employee compensation end-to-end — records, raises, payroll processing, and auditability — and get immediate answers about pay across countries, departments, and time.

**Persona:** HR Manager (single user).

## In scope

1. **Employee management** — create, view, update, and soft-delete employees (soft-delete preserves payroll history), with a server-side paginated, searchable, filterable directory of 10,000 records.
2. **Salary changes** — raise or correct an individual's salary by percentage or amount; every change is an append-only `SalaryChange` record (old → new, type, actor, timestamp). Corrections are just new changes — history is never edited.
3. **Bulk raises** — apply a raise to a cohort (by department/country/all) with a **preview** (headcount affected, estimated cost impact, employees recently raised flagged for optional exclusion). Each raise runs in its **own transaction**; failures don't block the batch.
4. **Raise guardrail** — any change pushing an employee's trailing-12-month cumulative raise above a **configurable threshold (default 30%, editable by the HR manager in settings)** is not auto-applied; it's parked in a **review queue** for manual approval, giving bulk runs organic partial outcomes (applied / needs-review).
5. **Payroll processing** — process salary for one employee or the whole org. Each run creates **immutable `SalaryCredit` records** snapshotting amount, currency, and USD conversion rate at credit time. **Idempotent by unique constraint** (employee, year, month) — a run can never double-pay.
6. **Salary hold** — employees can be placed on hold; processing skips them. Holds block payout, not compensation changes.
7. **Currency management** — settings page for ~10 currencies with manually managed USD conversion rates. Rate edits affect future credits and analytics only — never historical credits.
8. **Audit trail** — a centralized, append-only audit log capturing every change (profile edits, holds, deletions), with thin reference events for salary changes/credits (facts logged, amounts live in their own tables — complete trail, no duplication). Captured after business-transaction commit via transactional events. Global view collapses bulk runs into headers (expand = paginated drill-in); per-employee view shows that employee's full activity. **Keyset pagination** keeps reads constant-time at any depth; seeded with ~12 months of simulated history (300k+ rows) to demonstrate it.
9. **Analytics dashboard** — answers "how do we pay people": total monthly spend (USD-normalized), spend & headcount by country, average/median by department, salary distribution, employees on hold, last run summary.
10. **Authentication** — session-based login for the single seeded HR user.

## Deliberately out of scope (and why)

- **Per-country pay schedules** (India monthly, US biweekly, …) — pay-frequency calendaring is a payroll-engine concern orthogonal to what this exercise demonstrates; single monthly cycle assumed. First item I'd build next (per-country config + generalized run periods).
- **Pro-rated credits (mid-month joiners/leavers)** — proration rules are country- and policy-specific (calendar vs working days); this exercise assumes full-month credits. Design path: an `effective_days` factor on `SalaryCredit`, computed from join/deactivation dates against the org's proration policy — the immutable-snapshot model already accommodates it without schema change.
- **Taxes, deductions, allowances, payslips** — salary is one annual gross figure; payroll *accounting* is a different product.
- **Roles & multi-user access** — single-persona spec; would add role-based access + SSO in production.
- **Live FX feeds** — rate freshness is a data-ops concern; manually managed rates demonstrate the design without the operational surface.
- **Bulk Excel import** — migration is a one-time operation, not the product; the seed script plays that role here.
- **Clawback of paid credits** — credits are immutable facts; wrong raises are reversed going forward, real-money recovery is out of scope.
- **Kafka / partitioning / search infra for audit** — documented as the scale path; the shipped patterns (append-only writes, keyset pagination, deliberate indexes) carry to millions of rows before any of it is needed.

## Assumptions

- Single HR user; no concurrent-editor workflows (optimistic locking guards the edge).
- Salaries stored in local currency; USD normalization for comparisons uses current managed rates.
- Monthly pay cycle org-wide; credits are full-month.
- Seeded data is synthetic.

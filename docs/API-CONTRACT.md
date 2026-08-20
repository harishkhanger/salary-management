# API Contract

Design-first contract: backend and frontend are both written against this document.
Endpoints are marked **✅ built** or **⬜ pending** — this doubles as the build tracker.
When the frontend work starts, springdoc-openapi/Swagger becomes the live, generated
mirror of this contract; divergence is a bug.

## Conventions (apply to every endpoint)

- **Envelope**: every `/api/**` response is `{success, data, error}`.
  Success → `{success:true, data:<payload>, error:null}`; failure →
  `{success:false, data:null, error:{code, message}}`. Real HTTP status codes preserved.
  `DELETE` success is a bodyless `204`.
- **Error codes** (frontend branches on `error.code`, never message text):
  `NOT_FOUND` 404 · `VALIDATION` 400 · `DUPLICATE_CODE` 409 · `UNKNOWN_CURRENCY` 409 ·
  `STALE_VERSION` 409 · `CONCURRENT_MODIFICATION` 409 ·
  `STALE_PROPOSAL` 409 · `ALREADY_RESOLVED` 409 · `UNAUTHENTICATED` 401 (pending auth).
  New conditions get new codes; this list is the registry.
- **Offset pagination** (directory-style lists): request `page` (0-based) + `size`
  (clamped to `app.pagination.max-page-size`, 100); response
  `{content:[], page, size, totalElements, totalPages}`.
- **Keyset pagination** (audit feed only): request `cursor` (opaque, from previous
  response) + `limit`; response `{items:[], nextCursor}`. Constant-time at any depth.
- **Optimistic locking**: mutations on an employee carry the `version` the client
  loaded; mismatch → 409 `STALE_VERSION`.
- **Money**: decimal numbers, 2dp, local currency of the employee unless a field is
  explicitly `...Usd`. Timestamps are UTC ISO-8601.
- **Actor**: recorded from the session user (constant `"hr"` until auth ships).

---

## 1. Auth ⬜

| Method & path | Request | Response `data` |
|---|---|---|
| ⬜ `POST /api/auth/login` | `{username, password}` | `{username, name}` + session cookie |
| ⬜ `POST /api/auth/logout` | – | `204` |
| ⬜ `GET /api/auth/me` | – | `{username, name}` or 401 `UNAUTHENTICATED` |

Session-based (cookie), single seeded HR user. Every other `/api/**` route requires a
session once auth ships.

## 2. Employees ✅ (hold/release ⬜)

| Method & path | Request | Response `data` |
|---|---|---|
| ✅ `POST /api/employees` | `{employeeCode?, name, email, country, department, currencyCode, annualSalary, joinedOn}` | `Employee` (201; code generated `EMP-%05d` when omitted) |
| ✅ `GET /api/employees/{id}` | – | `Employee` |
| ✅ `PUT /api/employees/{id}` | `{name, email, country, department, currencyCode, joinedOn, version}` — profile only, never salary/status | `Employee` |
| ✅ `DELETE /api/employees/{id}` | – | `204` (soft delete; history preserved) |
| ✅ `GET /api/employees` | `?page&size&search&country&department&status` | offset page of `Employee` |
| ⬜ `PUT /api/employees/{id}/status` | `{status: ACTIVE\|ON_HOLD, version}` | `Employee` (salary hold / release) |

`Employee` = `{id, employeeCode, name, email, country, department, currencyCode,
annualSalary, status, joinedOn, version}`.

## 3. Salary changes ✅

| Method & path | Request | Response `data` |
|---|---|---|
| ✅ `POST /api/employees/{id}/salary-changes` | `{changeType: PERCENT\|AMOUNT\|CORRECTION, value, version}` — PERCENT: percent points; AMOUNT: delta; CORRECTION: absolute new salary | `Outcome` (below) |
| ✅ `GET /api/employees/{id}/salary-changes` | `?page&size` | offset page of `SalaryChange`, newest first |

`Outcome` = `{status: APPLIED\|PARKED_FOR_REVIEW, change?, employee?, reviewItemId?, reason?}`
— both statuses are 200; PARKED means the guardrail queued it and salary is untouched.
`SalaryChange` = `{id, employeeId, oldSalary, newSalary, changeType, percentValue,
actor, bulkRaiseRunId, createdAt}` (append-only).

## 4. Bulk raises ✅

| Method & path | Request | Response `data` |
|---|---|---|
| ✅ `POST /api/bulk-raises/preview` | `{raiseType: PERCENT\|AMOUNT, value, filterCountry?, filterDepartment?}` (no filters = whole org) | `{affectedCount, costImpact:[{currencyCode, current, proposed, delta}], costImpactUsdDelta, recentlyRaised:[{employeeId, employeeCode, name, lastRaiseAt}]}` |
| ✅ `POST /api/bulk-raises` | preview request + `{excludedEmployeeIds:[]}` | `BulkRaiseRun` summary |
| ✅ `GET /api/bulk-raises` | `?page&size` | offset page of `BulkRaiseRun` |

`BulkRaiseRun` = `{id, raiseType, raiseValue, filterCountry, filterDepartment,
appliedCount, reviewCount, excludedCount, createdAt}`.
Preview is a dry-run of the same code path. Execution: per-item transactions
(REQUIRES_NEW) — one bad record never blocks the cohort; guardrail parks per item.
Excluded employees are simply omitted, never queued.

## 5. Review queue ✅

| Method & path | Request | Response `data` |
|---|---|---|
| ✅ `GET /api/review-queue` | `?status=PENDING\|APPROVED\|REJECTED&page&size` | offset page of `ReviewItem` |
| ✅ `POST /api/review-queue/{id}/approve` | – | `Outcome` (applies via the standard change path; skips re-validation) |
| ✅ `POST /api/review-queue/{id}/reject` | – | `ReviewItem` (REJECTED, resolvedAt set) |

`ReviewItem` = `{id, employeeId, employeeCode?, name?, bulkRaiseRunId, proposedOld,
proposedNew, reason, status, createdAt, resolvedAt}`.
Approving applies `proposedNew` as a salary change attributed to the approver.

## 6. Payroll ⬜

| Method & path | Request | Response `data` |
|---|---|---|
| ⬜ `POST /api/payroll/runs` | `{year, month, employeeId?}` (omit employeeId = whole org) | `PayrollRun` = `{id, year, month, processedCount, skippedHeldCount, alreadyProcessedCount, initiatedBy, createdAt}` |
| ⬜ `GET /api/payroll/runs` | `?page&size` | offset page of `PayrollRun` |
| ⬜ `GET /api/employees/{id}/credits` | `?page&size` | offset page of `SalaryCredit` |

`SalaryCredit` = `{id, employeeId, year, month, amount, currencyCode, usdRate,
payrollRunId, createdAt}` — immutable snapshot; `usdRate` is the rate at credit time.
Idempotent: re-running a month counts already-credited employees in
`alreadyProcessedCount`, never double-pays (DB unique constraint is the referee).
Held employees are skipped and counted.

## 7. Currencies & settings ⬜

| Method & path | Request | Response `data` |
|---|---|---|
| ⬜ `GET /api/currencies` | – | `[{code, name, usdRate, updatedAt}]` |
| ⬜ `PUT /api/currencies/{code}` | `{usdRate}` | `Currency` (affects future credits/analytics only) |
| ⬜ `GET /api/settings` | – | `{raiseThresholdPercent}` |
| ⬜ `PUT /api/settings` | `{raiseThresholdPercent}` | `Settings` |

## 8. Audit feed ⬜

| Method & path | Request | Response `data` |
|---|---|---|
| ⬜ `GET /api/audit` | `?cursor&limit&entityType&entityId&runId` | `{items:[AuditEntry\|RunHeader], nextCursor}` |

Keyset-paginated (`(createdAt, id)` cursor). Global view collapses bulk-generated
rows: a `RunHeader` = `{kind:"RUN", runType: PAYROLL\|BULK_RAISE, runId, summary
counts, createdAt}` sourced from the run tables; expanding = same endpoint filtered
by `runId`. Per-employee activity = same endpoint filtered by
`entityType=EMPLOYEE&entityId`. `AuditEntry` = `{kind:"ENTRY", id, entityType,
entityId, action, actor, changedFields?, refTable?, refId?, runId?, createdAt}` —
thin references; amounts live in the referenced rows.

## 9. Analytics ⬜

| Method & path | Request | Response `data` |
|---|---|---|
| ⬜ `GET /api/analytics/summary` | – | `{totalMonthlySpendUsd, headcount, onHoldCount, lastPayrollRun:{year, month, processedCount, createdAt}}` |
| ⬜ `GET /api/analytics/by-country` | – | `[{country, headcount, monthlySpendUsd}]` |
| ⬜ `GET /api/analytics/by-department` | – | `[{department, headcount, avgAnnualUsd, medianAnnualUsd}]` |
| ⬜ `GET /api/analytics/salary-distribution` | – | `[{bucketFloorUsd, bucketCeilingUsd, count}]` |

All computed as SQL aggregates in the DB (never loading 10k rows), USD-normalized
via current rates.

---

## Build tracker

| Area | Status |
|---|---|
| Employees CRUD + directory | ✅ |
| Salary changes + guardrail parking | ✅ |
| Employee hold/release | ⬜ |
| Bulk raises + preview | ✅ |
| Review queue approve/reject | ✅ |
| Payroll runs + credits | ⬜ |
| Currencies & settings | ⬜ |
| Audit feed (keyset + run collapse) | ⬜ |
| Analytics | ⬜ |
| Auth (session) | ⬜ |

## Future work (post-assessment, deliberately deferred)

- **Excel-fed differentiated raise runs** — the real appraisal-cycle workflow:
  department heads submit per-employee percentages in a sheet; HR uploads it and the
  system executes per-employee raises. Slots into the existing machinery unchanged —
  per-employee `(employeeId, value)` pairs feeding the same per-item transaction path,
  validator pipeline, and `BulkRaiseRun` grouping; only a new input adapter (file
  parse + per-row error report) is needed. Deferred for the failure-UX scope
  (row-level validation errors, downloadable error report, template versioning).

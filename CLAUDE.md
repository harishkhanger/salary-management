# Salary Management System — build context

Take-home assessment (Incubyte). Read `docs/REQUIREMENTS.md` first — it is the scope contract. Nothing gets built beyond it.

## Working rules (non-negotiable)

1. **Build → Harish reviews → only then commit.** Never commit or push without explicit approval.
2. Incremental commits with meaningful messages — the history must tell the story of the build.
3. TDD rhythm where practical: failing test → implementation → refactor.
4. Scope is frozen by REQUIREMENTS.md. New ideas go to a "Future work" note, not into code.
5. Ask before assuming on anything ambiguous.

## Stack

- **Backend:** `backend/` — Spring Boot 4 (4.1.x parent; test annotations live in new per-module packages, e.g. `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`), Java 21, Maven. MySQL 8.4 (local via `docker-compose up -d`, host port **3307** to dodge the native mysqld on 3306), Flyway migrations (`ddl-auto: validate` — Hibernate never touches schema), `open-in-view: false`, Lombok.
- **Frontend (to build):** `frontend/` — React + Vite + one component library (MUI or Ant Design), dev proxy to backend `:8080`. Built after backend API stabilizes.
- **Tests:** Mockito-first for service-layer logic (fast, deterministic). Thin `@DataJpaTest` slice on test-scoped H2 covering ONLY database contracts: the payroll idempotency unique constraint, audit feed page-ordering contract, analytics aggregates.

## Design decisions (with the why — these are interview-defended)

- **Append-only compensation history.** Every salary change is a `SalaryChange` row (old → new, type, actor, timestamp). Corrections/reverts are new compensating entries. History is never edited or deleted.
- **Immutable payroll credits.** Processing creates `SalaryCredit` rows snapshotting amount, currency, and USD rate *at credit time*. Rate changes never touch history. Credits are facts; only decisions (changes) get reversed.
- **Idempotent payroll runs** via unique constraint `(employee_id, year, month)` — the DB is the referee; double-processing is structurally impossible. Process-one and process-all share one code path. Idempotency style: check-then-insert (skip-set queried upfront, counted as alreadyProcessed; constraint guards races). Payroll is the same durable-job idiom as bulk raises (V3: status + employee_id on payroll_runs; resume derives from salary_credits). Per-credit REQUIRES_NEW transactions (money = smallest blast radius, per Harish). Month rule (per Harish): past months always processable; current month only from day 25 (app.payroll.current-month-processable-from-day); future never.
- **Salary hold** = employee status (ACTIVE/ON_HOLD); processing skips held employees. Holds block payout, not compensation changes.
- **Bulk raises: per-item transactions**, not all-or-nothing — partial progress + review beats blocking a cohort for one bad record. Implemented with the per-item work in a SEPARATE bean (`REQUIRES_NEW`) because self-invocation would bypass the transaction proxy — structure exists BECAUSE of proxy semantics; keep it that way.
- **Bulk raise execution = durable background job (outbox-style, per Harish).** POST returns 202 instantly; the run row IS the job record (status QUEUED/RUNNING/COMPLETED, excluded_ids + initiated_by persisted). A single-threaded @Scheduled poller picks up QUEUED and crashed-RUNNING runs. Resume state is DERIVED from the append-only ledger (salary_changes/raise_review_items tagged with run_id) — no item table, crash never double-applies. Counts refresh every 100 items for progress polling.
- **Raise guardrail:** any change pushing trailing-12-month cumulative raise above a configurable threshold (OrgSettings, default 30%) is parked in a review queue (`RaiseReviewItem`), not auto-applied. Guardrail feeders: cumulative rule, band-less by design.
- **Bulk raise preview** (dry-run of the same code path): affected count, cost impact, and employees flagged for optional exclusion = those whose raises **total more than the guardrail threshold over their whole history** (current salary vs. the old_salary of their first recorded change; per Harish — replaced the earlier "raised in the last 90 days" window, which flagged a quarter of the company). Excluded = simply omitted, never queued.
- **Optimistic locking** (`@Version` on Employee) — bulk and individual operations can't silently overwrite each other.
- **Soft-delete employees** — payroll history must survive removal. Deletion is final (no restore, per Harish). Only `employee_code` is unique — globally, including deleted rows (recycling a code would poison history); names and emails are deliberately non-unique (namesakes and rehires must always be creatable; per Harish, no active-only email check either).
- **Audit ledger:** ONE centralized append-only `audit_log` table. Services publish `AuditEvent`s in-transaction; `AuditTrailListener` writes rows via `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + REQUIRES_NEW (trade-off documented: crash window vs business-txn decoupling; outbox is the production path; audit failures are swallowed, never propagate). Thin reference events for salary changes/credits (no amounts duplicated). **Run collapse = approach (b), per Harish:** jobs emit a RUN_COMPLETED audit row; global feed is ONE query (`run_id IS NULL OR RUN_COMPLETED`), headers enriched from run tables at read; expand = same endpoint with runId + runType (required — payroll and bulk run ids may collide). Per-employee view = same table filtered by entity, run items included. Currencies audit with entityId 0 (no numeric id), code in changed_fields.
- **Audit feed pagination = offset (numbered pages), per Harish** — same `PageResponse` shape and `Pagination` component as every other list, ordered `(created_at DESC, id DESC)` (id tiebreaker keeps page boundaries stable). Originally keyset (constant-time at any depth); switched for UX consistency. Measured cost on 140k rows: 0.1ms page 1, ~15ms at offset 100k, ~10ms COUNT — linear growth is the documented trade-off, keyset is the named scale path. Indexes: `(entity_id, created_at)`, `(created_at, id)`, `(run_id)`.
- **Seed script:** 10,000 employees across ~10 countries/currencies with realistic distributions, PLUS ~12 months of simulated history (payroll runs, scattered edits/raises) → ~140k audit rows / ~280k ledger rows total so the pagination/collapse story is demonstrable at scale. Java CommandLineRunner under the "seed" profile (JDBC batch inserts, explicit id counters for the thin-ref graph, deterministic Random(42), skip-guard); run: docker compose run --rm -e SPRING_PROFILES_ACTIVE=seed backend
- **Currency rates:** ~10 currencies, manually managed rates-to-USD (Settings page). Editing affects future credits/analytics only.
- **Auth:** minimal session-based login (Spring Security, V4-seeded HR user hr/BCrypt), everything under /api behind it except login. No roles. CSRF disabled — documented trade-off (pure-JSON same-origin SPA; production path: CookieCsrfTokenRepository). Controllers take actor from Principal; pollers from the run row's initiated_by.
- **Analytics:** SQL aggregates (GROUP BY) in the DB — never load 10k rows into memory. USD-normalized via current rates.
- **UX principle (per Harish, after the first end-to-end walkthrough): the UI guides the job, it does not expose the engine.** Vocabulary is the HR manager's ("pay July", "give Engineering 5%", "awaiting your review"), never the design doc's ("run", "queue", "ledger", ids). Concretely: Payroll is month-centric (`GET /payroll/months` → one row per month whose *state* decides the button label — the button says what will happen before the click, the result reads back as a sentence); Bulk raise is a 4-step flow (Who → What → Review → Confirm; nothing submits incomplete; finish = plain-language summary with a link to the review queue); Home (`/`) tracks state for the user (payroll due/partial, raises awaiting review, jobs running, last actions); Analytics moved to `/analytics`.

## API envelope (per Harish, MMT-style)

Every `/api/**` response is wrapped in `ApiResponse<T>`: success → `{success:true, data, error:null}`, failure → `{success:false, data:null, error:{code, message}}` with the real HTTP status preserved. Controllers return plain DTOs — `ApiResponseWrapper` (ResponseBodyAdvice) wraps automatically; `GlobalExceptionHandler` emits the same shape. Error codes are machine-readable (`NOT_FOUND`, `VALIDATION`, `DUPLICATE_CODE`, `UNKNOWN_CURRENCY`, `STALE_VERSION`, `CONCURRENT_MODIFICATION`, `STALE_PROPOSAL`, `ALREADY_RESOLVED`, `INTERNAL`, ...); the frontend branches on `error.code`, never message text. New error conditions get a new code, listed here. `GlobalExceptionHandler` also maps malformed/unparseable requests to `VALIDATION` and anything unforeseen to `INTERNAL` so the envelope never breaks. **Frontend turns codes into sentences in ONE place** (`api/errors.ts` → `humanize()`); pages never show raw codes or server prose except VALIDATION messages, which the server already phrases for people.

## Package layout (layer-based, per Harish)

`com.acme.salary.{entities, enums, repository, service, controller, dto/{request, response}, config, scheduler, ...}` — group by layer, not by feature. Entities in `entities/`, enums in `enums/`; add each layer folder when the first class of that layer appears. Pattern implementations get named subpackages under service: `service/strategy/` (RaiseCalculation + impls), `service/validation/` (RaiseValidator pipeline + RaiseContext); test packages mirror main.

## In-code patterns & LLD standards (per Harish)

SOLID and clean LLD everywhere, always: constructor injection only, single-responsibility classes, program to interfaces where multiple implementations exist or are foreseeable, no magic numbers (config properties or named constants), builders over telescoping setters, intention-revealing names (@Query over long derived method names), immutable DTOs (records). Named GoF patterns whenever they fit naturally — never forced where a simpler construct is honest. The two load-bearing ones so far:

- **Strategy** — raise types: `RaiseCalculation` interface, `PercentageRaise` / `FlatAmountRaise` implementations.
- **Validator pipeline** (pragmatic chain-of-responsibility) — guardrail as an ordered list of `RaiseValidator`s (amount sanity → cumulative-threshold → ...), each returning apply / park-for-review. Extensible where the domain actually extends.
- Do NOT introduce CQRS, sagas, event sourcing, or service decomposition — single-service monolith by design; docs stay lean (only what's built).
- Schema reference: `docs/DATABASE-DESIGN.md` (committed) — includes the usd_rate snapshot convention (local-per-USD, copied at insert, derived at read).
- API reference: `docs/API-CONTRACT.md` (committed) — design-first contract for every endpoint, doubles as the build tracker (✅/⬜ per area); update the tracker as increments land. springdoc/Swagger mirrors it once frontend work starts. Future-work items (e.g. Excel-fed differentiated raises) live at the bottom of that doc.

## Deliverables checklist (assessment requirements)

- [x] Backend API complete per requirements
- [ ] React UI: login, employee directory, employee detail (profile / change history / credit history / activity panels), bulk raise + preview + review queue, payroll processing, settings (currencies + guardrail threshold), global audit feed (collapse-by-run), analytics dashboard
- [x] Seed script (10k employees + 12 months history)
- [ ] Tests: Mockito suite + @DataJpaTest slice
- [ ] Deployed, publicly reachable
- [ ] Demo video
- [ ] README: run instructions, design decisions, trade-offs, AI-usage section, future work
- [ ] Incremental commit history throughout

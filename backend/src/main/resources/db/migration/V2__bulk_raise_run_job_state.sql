-- V2: bulk-raise execution becomes a durable background job (outbox-style).
-- The run row is the job record: status drives the poller; excluded_ids and
-- initiated_by are persisted so a crashed run can resume correctly.
-- Which items are already done is DERIVED from the append-only ledger
-- (salary_changes / raise_review_items tagged with the run id) — no item table.

ALTER TABLE bulk_raise_runs ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE bulk_raise_runs ADD COLUMN excluded_ids JSON NULL;
ALTER TABLE bulk_raise_runs ADD COLUMN initiated_by VARCHAR(50) NOT NULL DEFAULT 'hr';
ALTER TABLE bulk_raise_runs ADD CONSTRAINT chk_bulk_run_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED'));

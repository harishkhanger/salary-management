-- V1: complete baseline schema per docs/DATABASE-DESIGN.md
-- Status/type columns use VARCHAR + CHECK (not MySQL ENUM) so the same
-- migration runs on the H2 test slice; the invariant is identical.

CREATE TABLE currency_rates (
    code        VARCHAR(3)     NOT NULL,
    name        VARCHAR(60)    NOT NULL,
    usd_rate    DECIMAL(12,6)  NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT chk_currency_rate_positive CHECK (usd_rate > 0)
);

CREATE TABLE employees (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    employee_code  VARCHAR(20)    NOT NULL,
    name           VARCHAR(100)   NOT NULL,
    email          VARCHAR(150)   NOT NULL,
    country        VARCHAR(60)    NOT NULL,
    department     VARCHAR(60)    NOT NULL,
    currency_code  VARCHAR(3)     NOT NULL,
    annual_salary  DECIMAL(15,2)  NOT NULL,
    status         VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    joined_on      DATE           NOT NULL,
    deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    version        INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_employee_currency FOREIGN KEY (currency_code) REFERENCES currency_rates (code),
    CONSTRAINT chk_employee_status CHECK (status IN ('ACTIVE', 'ON_HOLD')),
    CONSTRAINT chk_employee_salary_positive CHECK (annual_salary >= 0)
);

CREATE INDEX idx_employees_directory_filters ON employees (deleted, country, department);
CREATE INDEX idx_employees_directory_search  ON employees (deleted, name);

CREATE TABLE payroll_runs (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    year                     SMALLINT     NOT NULL,
    month                    TINYINT      NOT NULL,
    processed_count          INT          NOT NULL DEFAULT 0,
    skipped_held_count       INT          NOT NULL DEFAULT 0,
    already_processed_count  INT          NOT NULL DEFAULT 0,
    initiated_by             VARCHAR(50)  NOT NULL,
    created_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_payroll_run_month CHECK (month BETWEEN 1 AND 12)
);

CREATE TABLE bulk_raise_runs (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    raise_type         VARCHAR(10)    NOT NULL,
    raise_value        DECIMAL(15,2)  NOT NULL,
    filter_country     VARCHAR(60)    NULL,
    filter_department  VARCHAR(60)    NULL,
    applied_count      INT            NOT NULL DEFAULT 0,
    review_count       INT            NOT NULL DEFAULT 0,
    excluded_count     INT            NOT NULL DEFAULT 0,
    created_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_bulk_raise_type CHECK (raise_type IN ('PERCENT', 'AMOUNT'))
);

CREATE TABLE salary_changes (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id        BIGINT         NOT NULL,
    old_salary         DECIMAL(15,2)  NOT NULL,
    new_salary         DECIMAL(15,2)  NOT NULL,
    change_type        VARCHAR(12)    NOT NULL,
    percent_value      DECIMAL(5,2)   NULL,
    actor              VARCHAR(50)    NOT NULL,
    bulk_raise_run_id  BIGINT         NULL,
    created_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_change_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_change_bulk_run FOREIGN KEY (bulk_raise_run_id) REFERENCES bulk_raise_runs (id),
    CONSTRAINT chk_change_type CHECK (change_type IN ('PERCENT', 'AMOUNT', 'CORRECTION'))
);

CREATE INDEX idx_changes_employee_history ON salary_changes (employee_id, created_at DESC);

CREATE TABLE salary_credits (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT         NOT NULL,
    year            SMALLINT       NOT NULL,
    month           TINYINT        NOT NULL,
    amount          DECIMAL(15,2)  NOT NULL,
    currency_code   VARCHAR(3)     NOT NULL,
    usd_rate        DECIMAL(12,6)  NOT NULL,
    payroll_run_id  BIGINT         NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_credit_employee_period UNIQUE (employee_id, year, month),
    CONSTRAINT fk_credit_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_credit_payroll_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs (id),
    CONSTRAINT chk_credit_month CHECK (month BETWEEN 1 AND 12)
);

CREATE INDEX idx_credits_employee_history ON salary_credits (employee_id, year DESC, month DESC);

CREATE TABLE raise_review_items (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id        BIGINT         NOT NULL,
    bulk_raise_run_id  BIGINT         NULL,
    proposed_old       DECIMAL(15,2)  NOT NULL,
    proposed_new       DECIMAL(15,2)  NOT NULL,
    reason             VARCHAR(255)   NOT NULL,
    status             VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    resolved_at        DATETIME(6)    NULL,
    created_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_review_bulk_run FOREIGN KEY (bulk_raise_run_id) REFERENCES bulk_raise_runs (id),
    CONSTRAINT chk_review_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_review_items_status ON raise_review_items (status);

CREATE TABLE org_settings (
    id                       BIGINT        NOT NULL,
    raise_threshold_percent  DECIMAL(5,2)  NOT NULL DEFAULT 30.00,
    PRIMARY KEY (id),
    CONSTRAINT chk_org_settings_single_row CHECK (id = 1),
    CONSTRAINT chk_threshold_positive CHECK (raise_threshold_percent > 0)
);

CREATE TABLE hr_users (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    username       VARCHAR(50)   NOT NULL,
    password_hash  VARCHAR(100)  NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_hr_username UNIQUE (username)
);

CREATE TABLE audit_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    entity_type     VARCHAR(30)   NOT NULL,
    entity_id       BIGINT        NOT NULL,
    action          VARCHAR(30)   NOT NULL,
    actor           VARCHAR(50)   NOT NULL,
    changed_fields  JSON          NULL,
    ref_table       VARCHAR(30)   NULL,
    ref_id          BIGINT        NULL,
    run_id          BIGINT        NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_audit_entity_activity ON audit_log (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_keyset_feed     ON audit_log (created_at DESC, id DESC);
CREATE INDEX idx_audit_run_drilldown   ON audit_log (run_id);

INSERT INTO org_settings (id, raise_threshold_percent) VALUES (1, 30.00);

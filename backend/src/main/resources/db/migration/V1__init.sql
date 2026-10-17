-- Fresh schema for the Financial Ledger. It mirrors expense-tracker's
-- prisma/schema.prisma (same tables, constraints and ON DELETE behaviour) with
-- UUID keys, join tables for tags and `sort_order` instead of the reserved
-- word `order`.
--
-- Accounting boundary: expenses, obligations and accounts never reference one
-- another. Account balances come only from opening_balance + account_event.

-- Categories -----------------------------------------------------------------

CREATE TABLE category (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL,
    parent_id   UUID        REFERENCES category (id) ON DELETE RESTRICT,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    archived    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT category_parent_name_key UNIQUE (parent_id, name)
);
CREATE INDEX category_parent_sort_idx ON category (parent_id, sort_order);

CREATE TABLE category_code (
    id          UUID PRIMARY KEY,
    code        TEXT        NOT NULL UNIQUE,
    category_id UUID        NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Imports (before expenses, which reference a batch) ----------------------------

CREATE TABLE import_batch (
    id            UUID PRIMARY KEY,
    file_hash     VARCHAR(64) NOT NULL UNIQUE,
    file_name     TEXT        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'STAGED'
                  CHECK (status IN ('STAGED', 'COMMITTED', 'FAILED')),
    total_rows    INTEGER     NOT NULL DEFAULT 0,
    expense_rows  INTEGER     NOT NULL DEFAULT 0,
    income_rows   INTEGER     NOT NULL DEFAULT 0,
    error_rows    INTEGER     NOT NULL DEFAULT 0,
    mapping_count INTEGER     NOT NULL DEFAULT 0,
    source_sheet  TEXT,
    dictionary    JSONB,
    committed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE import_row (
    id          UUID PRIMARY KEY,
    batch_id    UUID        NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    row_number  INTEGER     NOT NULL,
    normalized  JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT import_row_batch_row_key UNIQUE (batch_id, row_number)
);

CREATE TABLE import_row_error (
    import_row_id UUID    NOT NULL REFERENCES import_row (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,
    message       TEXT    NOT NULL,
    PRIMARY KEY (import_row_id, position)
);

-- Expenses and income ---------------------------------------------------------

CREATE TABLE expense_entry (
    id              UUID PRIMARY KEY,
    type            VARCHAR(16)    NOT NULL CHECK (type IN ('EXPENSE', 'INCOME')),
    amount          NUMERIC(18, 2) NOT NULL,
    entry_date      DATE           NOT NULL,
    category_id     UUID           REFERENCES category (id) ON DELETE SET NULL,
    recipient       TEXT           NOT NULL DEFAULT '',
    note            TEXT           NOT NULL DEFAULT '',
    source_code     TEXT,
    import_batch_id UUID           REFERENCES import_batch (id) ON DELETE SET NULL,
    source_row      INTEGER,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX expense_entry_date_idx ON expense_entry (entry_date);
CREATE INDEX expense_entry_category_idx ON expense_entry (category_id);
CREATE INDEX expense_entry_import_batch_idx ON expense_entry (import_batch_id);

CREATE TABLE expense_entry_tag (
    expense_entry_id UUID    NOT NULL REFERENCES expense_entry (id) ON DELETE CASCADE,
    position         INTEGER NOT NULL,
    tag              TEXT    NOT NULL,
    PRIMARY KEY (expense_entry_id, position)
);

CREATE TABLE categorization_rule (
    id          UUID PRIMARY KEY,
    keyword     TEXT        NOT NULL,
    category_id UUID        REFERENCES category (id) ON DELETE SET NULL,
    priority    INTEGER     NOT NULL DEFAULT 0,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX categorization_rule_enabled_priority_idx ON categorization_rule (enabled, priority);

CREATE TABLE categorization_rule_tag (
    categorization_rule_id UUID    NOT NULL REFERENCES categorization_rule (id) ON DELETE CASCADE,
    position               INTEGER NOT NULL,
    tag                    TEXT    NOT NULL,
    PRIMARY KEY (categorization_rule_id, position)
);

-- Obligations -------------------------------------------------------------------

CREATE TABLE obligation_party (
    id         UUID PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    note       TEXT        NOT NULL DEFAULT '',
    archived   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE obligation_case (
    id         UUID PRIMARY KEY,
    party_id   UUID        NOT NULL REFERENCES obligation_party (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    note       TEXT        NOT NULL DEFAULT '',
    closed     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT obligation_case_party_name_key UNIQUE (party_id, name)
);
CREATE INDEX obligation_case_party_closed_idx ON obligation_case (party_id, closed);

CREATE TABLE obligation_entry (
    id          UUID PRIMARY KEY,
    party_id    UUID           NOT NULL REFERENCES obligation_party (id) ON DELETE CASCADE,
    case_id     UUID           REFERENCES obligation_case (id) ON DELETE SET NULL,
    type        VARCHAR(16)    NOT NULL CHECK (type IN ('OUTLAY', 'REIMBURSEMENT')),
    amount      NUMERIC(18, 2) NOT NULL,
    entry_date  DATE           NOT NULL,
    category_id UUID           REFERENCES category (id) ON DELETE SET NULL,
    note        TEXT           NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX obligation_entry_party_date_idx ON obligation_entry (party_id, entry_date);
CREATE INDEX obligation_entry_case_idx ON obligation_entry (case_id);
CREATE INDEX obligation_entry_category_idx ON obligation_entry (category_id);

CREATE TABLE obligation_entry_tag (
    obligation_entry_id UUID    NOT NULL REFERENCES obligation_entry (id) ON DELETE CASCADE,
    position            INTEGER NOT NULL,
    tag                 TEXT    NOT NULL,
    PRIMARY KEY (obligation_entry_id, position)
);

-- Accounts ----------------------------------------------------------------------

CREATE TABLE financial_account (
    id              UUID PRIMARY KEY,
    name            TEXT           NOT NULL UNIQUE,
    description     TEXT           NOT NULL DEFAULT '',
    currency        VARCHAR(3)     NOT NULL,
    opening_balance NUMERIC(18, 2) NOT NULL,
    archived        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX financial_account_archived_idx ON financial_account (archived);

CREATE TABLE account_event (
    id                      UUID PRIMARY KEY,
    account_id              UUID           NOT NULL REFERENCES financial_account (id) ON DELETE CASCADE,
    counterparty_account_id UUID           REFERENCES financial_account (id) ON DELETE SET NULL,
    type                    VARCHAR(16)    NOT NULL
                            CHECK (type IN ('INCREASE', 'DECREASE', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT')),
    amount                  NUMERIC(18, 2) NOT NULL,
    event_date              DATE           NOT NULL,
    note                    TEXT           NOT NULL DEFAULT '',
    transfer_group_id       UUID,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX account_event_account_date_idx ON account_event (account_id, event_date);
CREATE INDEX account_event_transfer_group_idx ON account_event (transfer_group_id);

CREATE TABLE exchange_rate (
    id         UUID PRIMARY KEY,
    currency   VARCHAR(3)     NOT NULL,
    rate_date  DATE           NOT NULL,
    gel_rate   NUMERIC(18, 8) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT exchange_rate_currency_date_key UNIQUE (currency, rate_date)
);
CREATE INDEX exchange_rate_date_idx ON exchange_rate (rate_date);

-- Fitness -------------------------------------------------------------------------
-- Sort-order uniques are DEFERRABLE so reordering can swap rows inside one
-- transaction regardless of the order Hibernate flushes the updates in.

CREATE TABLE exercise (
    id                 UUID PRIMARY KEY,
    name               TEXT          NOT NULL UNIQUE,
    muscle_group       TEXT          NOT NULL DEFAULT '',
    note               TEXT          NOT NULL DEFAULT '',
    recommended_weight NUMERIC(8, 2) NOT NULL DEFAULT 0,
    recommended_reps   INTEGER       NOT NULL DEFAULT 10,
    recommended_sets   INTEGER,
    archived           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX exercise_archived_name_idx ON exercise (archived, name);

-- A variation of the same movement (e.g. incline vs flat chest press). It shares
-- the plan slot but keeps its own next-workout target and logged history.
CREATE TABLE exercise_variation (
    id                 UUID PRIMARY KEY,
    exercise_id        UUID          NOT NULL REFERENCES exercise (id) ON DELETE CASCADE,
    name               TEXT          NOT NULL,
    note               TEXT          NOT NULL DEFAULT '',
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    archived           BOOLEAN       NOT NULL DEFAULT FALSE,
    recommended_weight NUMERIC(8, 2) NOT NULL DEFAULT 0,
    recommended_reps   INTEGER       NOT NULL DEFAULT 10,
    recommended_sets   INTEGER,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT exercise_variation_exercise_name_key UNIQUE (exercise_id, name)
);
CREATE INDEX exercise_variation_exercise_archived_sort_idx
    ON exercise_variation (exercise_id, archived, sort_order);

CREATE TABLE workout_plan (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL UNIQUE,
    description TEXT        NOT NULL DEFAULT '',
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    archived    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX workout_plan_archived_sort_idx ON workout_plan (archived, sort_order);

CREATE TABLE workout_plan_day (
    id         UUID PRIMARY KEY,
    plan_id    UUID        NOT NULL REFERENCES workout_plan (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    sort_order INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT workout_plan_day_plan_sort_key UNIQUE (plan_id, sort_order) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE plan_exercise (
    id             UUID PRIMARY KEY,
    plan_day_id    UUID        NOT NULL REFERENCES workout_plan_day (id) ON DELETE CASCADE,
    exercise_id    UUID        NOT NULL REFERENCES exercise (id) ON DELETE RESTRICT,
    sort_order     INTEGER     NOT NULL,
    set_count      INTEGER     NOT NULL,
    -- Consecutive slots sharing a group are one superset: their sets alternate
    -- (A1, B1, A2, B2, ...) and both are rated afterwards.
    superset_group INTEGER,
    note           TEXT        NOT NULL DEFAULT '',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT plan_exercise_day_exercise_key UNIQUE (plan_day_id, exercise_id),
    CONSTRAINT plan_exercise_day_sort_key UNIQUE (plan_day_id, sort_order) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX plan_exercise_exercise_idx ON plan_exercise (exercise_id);

CREATE TABLE workout_session (
    id           UUID PRIMARY KEY,
    plan_day_id  UUID        NOT NULL REFERENCES workout_plan_day (id) ON DELETE RESTRICT,
    started_at   TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    note         TEXT        NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX workout_session_day_completed_idx ON workout_session (plan_day_id, completed_at);
CREATE INDEX workout_session_completed_idx ON workout_session (completed_at);

CREATE TABLE workout_exercise (
    id               UUID PRIMARY KEY,
    workout_id       UUID          NOT NULL REFERENCES workout_session (id) ON DELETE CASCADE,
    plan_exercise_id UUID          REFERENCES plan_exercise (id) ON DELETE SET NULL,
    exercise_id      UUID          NOT NULL REFERENCES exercise (id) ON DELETE RESTRICT,
    variation_id     UUID          REFERENCES exercise_variation (id) ON DELETE SET NULL,
    sort_order       INTEGER       NOT NULL,
    superset_group   INTEGER,
    target_weight    NUMERIC(8, 2) NOT NULL,
    target_reps      INTEGER       NOT NULL,
    perceived_effort INTEGER       NOT NULL DEFAULT 2,
    next_weight      NUMERIC(8, 2),
    next_reps        INTEGER,
    next_sets        INTEGER,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX workout_exercise_workout_sort_idx ON workout_exercise (workout_id, sort_order);
CREATE INDEX workout_exercise_exercise_idx ON workout_exercise (exercise_id);
CREATE INDEX workout_exercise_variation_idx ON workout_exercise (variation_id);
CREATE INDEX workout_exercise_plan_exercise_idx ON workout_exercise (plan_exercise_id);

CREATE TABLE workout_set (
    id                  UUID PRIMARY KEY,
    workout_exercise_id UUID          NOT NULL REFERENCES workout_exercise (id) ON DELETE CASCADE,
    set_number          INTEGER       NOT NULL,
    weight              NUMERIC(8, 2) NOT NULL,
    reps                INTEGER       NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT workout_set_exercise_number_key UNIQUE (workout_exercise_id, set_number)
);

-- A workout that has been started but not finished. It lives in the database so
-- a session survives a closed window and can be resumed from any device.
CREATE TABLE active_workout (
    id          UUID PRIMARY KEY,
    plan_day_id UUID        NOT NULL UNIQUE REFERENCES workout_plan_day (id) ON DELETE CASCADE,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    note        TEXT        NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX active_workout_started_idx ON active_workout (started_at);

CREATE TABLE active_workout_exercise (
    id                UUID PRIMARY KEY,
    active_workout_id UUID          NOT NULL REFERENCES active_workout (id) ON DELETE CASCADE,
    plan_exercise_id  UUID          REFERENCES plan_exercise (id) ON DELETE SET NULL,
    exercise_id       UUID          NOT NULL REFERENCES exercise (id) ON DELETE RESTRICT,
    variation_id      UUID          REFERENCES exercise_variation (id) ON DELETE SET NULL,
    sort_order        INTEGER       NOT NULL,
    superset_group    INTEGER,
    target_weight     NUMERIC(8, 2) NOT NULL,
    target_reps       INTEGER       NOT NULL,
    perceived_effort  INTEGER,
    next_weight       NUMERIC(8, 2),
    next_reps         INTEGER,
    next_sets         INTEGER,
    wrapped_up_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT active_workout_exercise_workout_sort_key
        UNIQUE (active_workout_id, sort_order) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX active_workout_exercise_exercise_idx ON active_workout_exercise (exercise_id);
CREATE INDEX active_workout_exercise_variation_idx ON active_workout_exercise (variation_id);
CREATE INDEX active_workout_exercise_plan_exercise_idx ON active_workout_exercise (plan_exercise_id);

CREATE TABLE active_workout_set (
    id                         UUID PRIMARY KEY,
    active_workout_exercise_id UUID          NOT NULL REFERENCES active_workout_exercise (id) ON DELETE CASCADE,
    set_number                 INTEGER       NOT NULL,
    weight                     NUMERIC(8, 2) NOT NULL,
    reps                       INTEGER       NOT NULL,
    completed                  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT active_workout_set_exercise_number_key UNIQUE (active_workout_exercise_id, set_number)
);

-- Authentication ----------------------------------------------------------------
-- Every login attempt, for per-username and per-IP rate limiting and alerting.

CREATE TABLE login_attempt (
    id           UUID PRIMARY KEY,
    username     TEXT        NOT NULL,
    ip_address   VARCHAR(64) NOT NULL,
    succeeded    BOOLEAN     NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX login_attempt_username_idx ON login_attempt (username, attempted_at);
CREATE INDEX login_attempt_ip_idx ON login_attempt (ip_address, attempted_at);

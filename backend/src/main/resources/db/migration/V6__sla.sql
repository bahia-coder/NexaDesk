-- One SLA rule per priority. Changing a rule only affects tickets created afterwards: existing tickets keep the
-- deadlines computed when they were created.
CREATE TABLE sla_configurations (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    priority_id               BIGINT      NOT NULL UNIQUE REFERENCES priorities (id),
    first_response_minutes    INT         NOT NULL,
    resolution_minutes        INT         NOT NULL,
    business_hours_only       BOOLEAN     NOT NULL,
    warning_threshold_percent INT         NOT NULL DEFAULT 75 CHECK (warning_threshold_percent BETWEEN 50 AND 95),
    version                   INT         NOT NULL DEFAULT 0,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (first_response_minutes > 0 AND resolution_minutes > first_response_minutes)
);

-- Defaults from the specification: critical tickets run 24/7, the others in business hours.
INSERT INTO sla_configurations (priority_id, first_response_minutes, resolution_minutes, business_hours_only)
SELECT p.id, v.first_response, v.resolution, v.business_hours
FROM priorities p
JOIN (VALUES ('CRITICAL', 15, 120, FALSE),
             ('HIGH', 60, 480, TRUE),
             ('MEDIUM', 240, 1440, TRUE),
             ('LOW', 480, 2880, TRUE)) AS v (code, first_response, resolution, business_hours)
  ON v.code = p.code;

-- Single-row table: the company's working hours. The time zone is deployment configuration (APP_TIMEZONE).
CREATE TABLE business_calendar (
    id           INT         PRIMARY KEY CHECK (id = 1),
    work_start   TIME        NOT NULL,
    work_end     TIME        NOT NULL,
    working_days VARCHAR(70) NOT NULL,
    CHECK (work_end > work_start)
);

INSERT INTO business_calendar (id, work_start, work_end, working_days)
VALUES (1, '08:00', '18:00', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY');

CREATE TABLE holidays (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    day   DATE         NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

-- SLA tracking on tickets. Nullable: tickets created before this migration simply have no SLA.
ALTER TABLE tickets
    ADD COLUMN sla_business_hours    BOOLEAN,
    ADD COLUMN first_response_due_at TIMESTAMPTZ,
    ADD COLUMN resolution_due_at     TIMESTAMPTZ,
    ADD COLUMN first_responded_at    TIMESTAMPTZ,
    ADD COLUMN sla_paused_at         TIMESTAMPTZ,
    ADD COLUMN sla_paused_minutes    INT NOT NULL DEFAULT 0,
    ADD COLUMN sla_warning_sent_at   TIMESTAMPTZ,
    ADD COLUMN sla_breach_sent_at    TIMESTAMPTZ;

-- The SLA monitor only scans running tickets (partial index).
CREATE INDEX idx_tickets_sla_running ON tickets (resolution_due_at)
    WHERE status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS');

-- Work log of a technician on a ticket: diagnosis, applied solution and time spent.
CREATE TABLE interventions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id          BIGINT      NOT NULL REFERENCES tickets (id),
    technician_id      BIGINT      NOT NULL REFERENCES users (id),
    diagnosis          TEXT        NOT NULL,
    solution           TEXT,
    time_spent_minutes INT         NOT NULL CHECK (time_spent_minutes BETWEEN 1 AND 1440),
    started_at         TIMESTAMPTZ,
    ended_at           TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_interventions_period CHECK (started_at IS NULL OR ended_at IS NULL OR ended_at > started_at)
);

CREATE INDEX idx_interventions_ticket ON interventions (ticket_id, created_at);
CREATE INDEX idx_interventions_technician ON interventions (technician_id, created_at DESC);

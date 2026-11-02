-- Security and administration trail (admin only). Distinct from ticket_history, the per-ticket timeline shown
-- to the people involved: different audience, scope and level of detail.
CREATE TABLE audit_logs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    -- Copied at write time: the entry stays readable if the account is later renamed or anonymised.
    actor_email VARCHAR(255),
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   VARCHAR(40),
    summary     VARCHAR(500) NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    trace_id    VARCHAR(16),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id, created_at DESC);

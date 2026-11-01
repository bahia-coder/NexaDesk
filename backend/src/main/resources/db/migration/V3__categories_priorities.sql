-- Ticket categories and subcategories: configured by the administrator, never hard-deleted
-- (tickets keep referencing them), deactivated instead.
CREATE TABLE ticket_categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    icon        VARCHAR(40)  NOT NULL DEFAULT 'tag',
    keywords    VARCHAR(1000),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- Names are unique regardless of case ("Matériel" and "matériel" are the same category).
CREATE UNIQUE INDEX uq_ticket_categories_name ON ticket_categories (lower(name));

CREATE TABLE ticket_subcategories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id BIGINT      NOT NULL REFERENCES ticket_categories (id),
    name        VARCHAR(80) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_ticket_subcategories_name ON ticket_subcategories (category_id, lower(name));

-- Priorities are reference data (one row per PriorityCode); only label and color are editable.
-- Their SLA rules are added by the SLA migration.
CREATE TABLE priorities (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code  VARCHAR(20) NOT NULL UNIQUE CHECK (code IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    label VARCHAR(50) NOT NULL,
    rank  INT         NOT NULL UNIQUE CHECK (rank BETWEEN 1 AND 4),
    color VARCHAR(7)  NOT NULL CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

INSERT INTO priorities (code, label, rank, color) VALUES
    ('LOW', 'Basse', 1, '#64748B'),
    ('MEDIUM', 'Moyenne', 2, '#2563EB'),
    ('HIGH', 'Haute', 3, '#EA580C'),
    ('CRITICAL', 'Critique', 4, '#DC2626');

-- Categories a technician is qualified for: drives self-assignment rights and "new ticket" notifications.
CREATE TABLE technician_specialties (
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES ticket_categories (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, category_id)
);

CREATE INDEX idx_technician_specialties_category ON technician_specialties (category_id);

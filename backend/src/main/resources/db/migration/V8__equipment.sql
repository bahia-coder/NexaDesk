-- IT asset inventory. Codes EQ-0001… come from a sequence (concurrency-safe, never reused).
CREATE SEQUENCE equipment_code_seq;

CREATE TABLE equipment (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inventory_code    VARCHAR(20)   NOT NULL UNIQUE,
    name              VARCHAR(120)  NOT NULL,
    type              VARCHAR(20)   NOT NULL
        CHECK (type IN ('LAPTOP', 'DESKTOP', 'MONITOR', 'PRINTER', 'KEYBOARD', 'MOUSE', 'SERVER', 'NETWORK',
                        'PHONE', 'OTHER')),
    brand             VARCHAR(60),
    model             VARCHAR(80),
    serial_number     VARCHAR(80) UNIQUE,
    acquisition_date  DATE,
    warranty_end_date DATE,
    status            VARCHAR(20)   NOT NULL CHECK (status IN ('IN_STOCK', 'IN_USE', 'IN_REPAIR', 'RETIRED')),
    -- Where a shared device lives (printer, server…), or the desk of the user it is assigned to.
    location          VARCHAR(120),
    assigned_user_id  BIGINT REFERENCES users (id),
    notes             VARCHAR(1000),
    version           INT           NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CHECK (warranty_end_date IS NULL OR acquisition_date IS NULL OR warranty_end_date >= acquisition_date),
    -- In stock or retired means nobody holds it.
    CHECK (status NOT IN ('IN_STOCK', 'RETIRED') OR assigned_user_id IS NULL)
);

CREATE INDEX idx_equipment_assigned_user ON equipment (assigned_user_id);
CREATE INDEX idx_equipment_status ON equipment (status);

-- Who held which device, and when.
CREATE TABLE equipment_assignments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    equipment_id BIGINT      NOT NULL REFERENCES equipment (id),
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    assigned_at  TIMESTAMPTZ NOT NULL,
    returned_at  TIMESTAMPTZ,
    CHECK (returned_at IS NULL OR returned_at >= assigned_at)
);

CREATE INDEX idx_equipment_assignments_equipment ON equipment_assignments (equipment_id, assigned_at DESC);
-- At most one open assignment per device.
CREATE UNIQUE INDEX uq_equipment_assignments_open ON equipment_assignments (equipment_id) WHERE returned_at IS NULL;

ALTER TABLE tickets ADD COLUMN equipment_id BIGINT REFERENCES equipment (id);
CREATE INDEX idx_tickets_equipment ON tickets (equipment_id);

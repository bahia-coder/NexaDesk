-- One counter per (ticket type, year), incremented atomically by an INSERT ... ON CONFLICT DO UPDATE.
CREATE TABLE ticket_reference_counters (
    type       VARCHAR(20) NOT NULL,
    year       INT         NOT NULL,
    last_value INT         NOT NULL,
    PRIMARY KEY (type, year)
);

CREATE TABLE tickets (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference        VARCHAR(20)   NOT NULL UNIQUE,
    type             VARCHAR(20)   NOT NULL CHECK (type IN ('INCIDENT', 'SERVICE_REQUEST')),
    title            VARCHAR(150)  NOT NULL CHECK (char_length(title) BETWEEN 5 AND 150),
    description      TEXT          NOT NULL,
    status           VARCHAR(20)   NOT NULL
        CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED', 'REJECTED')),
    impact           VARCHAR(10)   NOT NULL CHECK (impact IN ('LOW', 'MEDIUM', 'HIGH')),
    urgency          VARCHAR(10)   NOT NULL CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH')),
    priority_id      BIGINT        NOT NULL REFERENCES priorities (id),
    category_id      BIGINT        NOT NULL REFERENCES ticket_categories (id),
    subcategory_id   BIGINT REFERENCES ticket_subcategories (id),
    requester_id     BIGINT        NOT NULL REFERENCES users (id),
    assignee_id      BIGINT REFERENCES users (id),
    pending_reason   VARCHAR(20)
        CHECK (pending_reason IN ('WAITING_USER', 'WAITING_VENDOR', 'WAITING_PART', 'OTHER')),
    rejection_reason VARCHAR(1000),
    resolution       TEXT,
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    reopen_count     INT           NOT NULL DEFAULT 0,
    version          INT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    -- Safety nets for the workflow rules implemented in TicketPolicy / TicketWorkflowService:
    CONSTRAINT chk_tickets_assignee_when_in_progress
        CHECK (status NOT IN ('ASSIGNED', 'IN_PROGRESS', 'PENDING', 'RESOLVED') OR assignee_id IS NOT NULL),
    CONSTRAINT chk_tickets_pending_reason CHECK (status <> 'PENDING' OR pending_reason IS NOT NULL),
    CONSTRAINT chk_tickets_rejection_reason CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT chk_tickets_resolution CHECK (status NOT IN ('RESOLVED', 'CLOSED') OR resolution IS NOT NULL)
);

CREATE INDEX idx_tickets_status ON tickets (status);
CREATE INDEX idx_tickets_assignee_status ON tickets (assignee_id, status);
CREATE INDEX idx_tickets_requester_status ON tickets (requester_id, status);
CREATE INDEX idx_tickets_category ON tickets (category_id);
CREATE INDEX idx_tickets_priority ON tickets (priority_id);
CREATE INDEX idx_tickets_created_at ON tickets (created_at DESC);

CREATE TABLE ticket_comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id),
    author_id  BIGINT      NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    internal   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ticket_comments_ticket ON ticket_comments (ticket_id, created_at);

CREATE TABLE ticket_attachments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL REFERENCES tickets (id),
    uploaded_by   BIGINT       NOT NULL REFERENCES users (id),
    original_name VARCHAR(255) NOT NULL,
    storage_key   VARCHAR(100) NOT NULL UNIQUE,
    content_type  VARCHAR(150) NOT NULL,
    size_bytes    BIGINT       NOT NULL CHECK (size_bytes > 0),
    sha256        VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_ticket_attachments_ticket ON ticket_attachments (ticket_id);

-- Business timeline of a ticket. actor_id is NULL for system actions (auto-close, SLA alerts).
-- internal = true hides the entry from the requester (e.g. an internal comment was added).
CREATE TABLE ticket_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT        NOT NULL REFERENCES tickets (id),
    actor_id   BIGINT REFERENCES users (id),
    action     VARCHAR(30)   NOT NULL,
    field      VARCHAR(50),
    old_value  VARCHAR(500),
    new_value  VARCHAR(500),
    comment    VARCHAR(1000),
    internal   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_ticket_history_ticket ON ticket_history (ticket_id, created_at);

CREATE TABLE ticket_feedback (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL UNIQUE REFERENCES tickets (id),
    author_id  BIGINT      NOT NULL REFERENCES users (id),
    rating     SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);

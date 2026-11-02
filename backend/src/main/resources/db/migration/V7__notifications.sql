-- In-app notifications: stored (notification centre, unread badge, history) and pushed over WebSocket.
CREATE TABLE notifications (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(150) NOT NULL,
    message      VARCHAR(500) NOT NULL,
    ticket_id    BIGINT REFERENCES tickets (id) ON DELETE CASCADE,
    link         VARCHAR(200),
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
-- Unread badge: counts only the unread rows of one user.
CREATE INDEX idx_notifications_unread ON notifications (recipient_id) WHERE read_at IS NULL;

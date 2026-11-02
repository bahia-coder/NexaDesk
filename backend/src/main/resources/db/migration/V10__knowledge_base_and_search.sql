-- Full-text search (French stemming) on tickets, for similar-incident detection. The vector is a generated column:
-- PostgreSQL keeps it in sync, no trigger and no application code. Weights: title > description > resolution.
ALTER TABLE tickets ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('french', coalesce(title, '')), 'A')
    || setweight(to_tsvector('french', coalesce(description, '')), 'B')
    || setweight(to_tsvector('french', coalesce(resolution, '')), 'C')) STORED;

CREATE INDEX idx_tickets_search ON tickets USING GIN (search_vector);
-- Trigram index: fuzzy title matching (typos, word variants that stemming misses).
CREATE INDEX idx_tickets_title_trgm ON tickets USING GIN (title gin_trgm_ops);

CREATE TABLE knowledge_articles (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title            VARCHAR(150) NOT NULL CHECK (char_length(title) BETWEEN 5 AND 150),
    summary          VARCHAR(500) NOT NULL,
    content          TEXT         NOT NULL,
    category_id      BIGINT       NOT NULL REFERENCES ticket_categories (id),
    author_id        BIGINT       NOT NULL REFERENCES users (id),
    -- Ticket whose solution the article was drafted from (at most one article per ticket).
    source_ticket_id BIGINT UNIQUE REFERENCES tickets (id),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    view_count       INT          NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    helpful_count    INT          NOT NULL DEFAULT 0 CHECK (helpful_count >= 0),
    published_at     TIMESTAMPTZ,
    version          INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    search_vector    tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('french', coalesce(title, '')), 'A')
        || setweight(to_tsvector('french', coalesce(summary, '')), 'B')
        || setweight(to_tsvector('french', coalesce(content, '')), 'C')) STORED,
    CONSTRAINT chk_knowledge_published_at CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_knowledge_search ON knowledge_articles USING GIN (search_vector);
CREATE INDEX idx_knowledge_title_trgm ON knowledge_articles USING GIN (title gin_trgm_ops);
CREATE INDEX idx_knowledge_status_category ON knowledge_articles (status, category_id);

-- "This article helped me": one vote per user, enforced by the primary key.
CREATE TABLE knowledge_article_votes (
    article_id BIGINT      NOT NULL REFERENCES knowledge_articles (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (article_id, user_id)
);

-- Articles a technician used to solve a ticket.
CREATE TABLE ticket_knowledge_links (
    ticket_id  BIGINT      NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    article_id BIGINT      NOT NULL REFERENCES knowledge_articles (id) ON DELETE CASCADE,
    linked_by  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (ticket_id, article_id)
);

CREATE INDEX idx_ticket_knowledge_links_article ON ticket_knowledge_links (article_id);

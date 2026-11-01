-- Trigram similarity, used for fuzzy search and similar-ticket detection.
-- pg_trgm is a trusted extension (PostgreSQL 13+): the database owner can create it without superuser rights.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

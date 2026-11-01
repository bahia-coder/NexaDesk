-- Roles are reference data: one row per RoleCode enum value.
CREATE TABLE roles (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code  VARCHAR(20) NOT NULL UNIQUE CHECK (code IN ('EMPLOYEE', 'TECHNICIAN', 'ADMIN')),
    label VARCHAR(50) NOT NULL
);

INSERT INTO roles (code, label) VALUES
    ('EMPLOYEE', 'Employé'),
    ('TECHNICIAN', 'Technicien'),
    ('ADMIN', 'Administrateur');

CREATE TABLE departments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash         VARCHAR(100) NOT NULL,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    job_title             VARCHAR(120),
    phone                 VARCHAR(30),
    avatar_color          VARCHAR(7),
    role_id               BIGINT       NOT NULL REFERENCES roles (id),
    department_id         BIGINT REFERENCES departments (id),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    version               INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_users_role ON users (role_id);
CREATE INDEX idx_users_department ON users (department_id);

-- Opaque refresh tokens: only a SHA-256 hash is stored. Tokens issued from the same login share a
-- family_id so that reuse of a rotated token revokes the whole family (theft detection).
CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id  UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

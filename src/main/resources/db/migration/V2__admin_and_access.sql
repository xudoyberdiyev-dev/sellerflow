-- Web Superadmin: adminlar, sessiyalar, rozilik asosidagi vaqtinchalik kirish (PRD-07 V1 minimal)

CREATE TABLE admins (
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(16)  NOT NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    totp_secret           VARCHAR(64),
    totp_enabled          BOOLEAN      NOT NULL DEFAULT false,
    must_change_password  BOOLEAN      NOT NULL DEFAULT false,
    failed_attempts       INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at         TIMESTAMPTZ,
    CONSTRAINT admins_role_chk CHECK (role IN ('SUPERADMIN','SUPPORT')),
    CONSTRAINT admins_status_chk CHECK (status IN ('ACTIVE','DISABLED'))
);
CREATE UNIQUE INDEX admins_email_uq ON admins (lower(email));

-- Token o'zi saqlanmaydi, faqat SHA-256 xeshi
CREATE TABLE admin_sessions (
    token_hash    VARCHAR(64)  PRIMARY KEY,
    admin_id      BIGINT       NOT NULL REFERENCES admins(id),
    kind          VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT false,
    CONSTRAINT admin_sessions_kind_chk CHECK (kind IN ('FULL','TOTP_SETUP'))
);
CREATE INDEX admin_sessions_admin_idx ON admin_sessions (admin_id);

CREATE TABLE access_grants (
    id             BIGSERIAL PRIMARY KEY,
    business_id    BIGINT        NOT NULL REFERENCES businesses(id),
    admin_id       BIGINT        NOT NULL REFERENCES admins(id),
    purpose        VARCHAR(300)  NOT NULL,
    scope          VARCHAR(32)   NOT NULL DEFAULT 'PRODUCTS_AND_HISTORY',
    minutes        INTEGER       NOT NULL CHECK (minutes BETWEEN 30 AND 60),
    status         VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    requested_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_at     TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    revoked_by     VARCHAR(16),
    CONSTRAINT access_grants_status_chk CHECK (status IN ('REQUESTED','APPROVED','DECLINED','REVOKED','EXPIRED'))
);
CREATE INDEX access_grants_admin_idx ON access_grants (admin_id, requested_at DESC);
CREATE INDEX access_grants_business_idx ON access_grants (business_id, status);

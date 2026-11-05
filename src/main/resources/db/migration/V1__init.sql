-- SellerFlow V1.0 Closed Beta — boshlang'ich sxema
-- Pul: NUMERIC(19,2) (UZS). Foiz: NUMERIC(9,4) (10 = 10%).
-- Barcha tenantga tegishli yozuvlarda business_id bor.

CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    telegram_user_id       BIGINT       NOT NULL UNIQUE,
    status                 VARCHAR(32)  NOT NULL,
    deletion_requested_at  TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_status_chk CHECK (status IN ('ONBOARDING','ACTIVE','SUSPENDED','DELETION_REQUESTED','CLOSED'))
);

CREATE TABLE businesses (
    id               BIGSERIAL PRIMARY KEY,
    owner_user_id    BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    business_name    VARCHAR(80),
    display_name     VARCHAR(60),
    primary_channel  VARCHAR(32)  NOT NULL,
    language         VARCHAR(16)  NOT NULL DEFAULT 'uz',
    currency         VARCHAR(8)   NOT NULL DEFAULT 'UZS',
    timezone         VARCHAR(64)  NOT NULL DEFAULT 'Asia/Tashkent',
    plan_code        VARCHAR(32)  NOT NULL,
    version          INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT businesses_channel_chk CHECK (primary_channel IN
        ('UZUM','WILDBERRIES','OZON','INSTAGRAM','TELEGRAM_SALES','OFFLINE_STORE','OTHER'))
);

CREATE TABLE consents (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id),
    terms_version  VARCHAR(64)  NOT NULL,
    accepted_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT consents_unique UNIQUE (user_id, terms_version)
);

-- Bitta foydalanuvchi = bitta faol interaktiv jarayon (PK = user_id)
CREATE TABLE telegram_flows (
    user_id         BIGINT       PRIMARY KEY REFERENCES users(id),
    flow_type       VARCHAR(32),
    step            VARCHAR(64),
    data            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    revision        INTEGER      NOT NULL DEFAULT 0,
    ui_mode         VARCHAR(32),
    ui_data         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_input_at   TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id                  BIGSERIAL PRIMARY KEY,
    business_id         BIGINT        NOT NULL REFERENCES businesses(id),
    name                VARCHAR(120)  NOT NULL,
    unit_cost           NUMERIC(19,2) NOT NULL CHECK (unit_cost >= 0),
    planned_sale_price  NUMERIC(19,2) CHECK (planned_sale_price IS NULL OR planned_sale_price > 0),
    sku                 VARCHAR(64),
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    version             INTEGER       NOT NULL DEFAULT 0,
    idempotency_key     VARCHAR(80)   UNIQUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    archived_at         TIMESTAMPTZ,
    CONSTRAINT products_status_chk CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE UNIQUE INDEX products_business_sku_uq ON products (business_id, lower(sku)) WHERE sku IS NOT NULL;
CREATE INDEX products_business_status_idx ON products (business_id, status, id DESC);

CREATE TABLE calculation_history (
    id                     BIGSERIAL PRIMARY KEY,
    business_id            BIGINT        NOT NULL REFERENCES businesses(id),
    user_id                BIGINT        NOT NULL REFERENCES users(id),
    result_id              VARCHAR(64)   NOT NULL UNIQUE,
    calculation_type       VARCHAR(16)   NOT NULL,
    product_id             BIGINT        REFERENCES products(id),
    product_name_snapshot  VARCHAR(120),
    channel_snapshot       VARCHAR(32)   NOT NULL,
    currency               VARCHAR(8)    NOT NULL DEFAULT 'UZS',
    input_snapshot         JSONB         NOT NULL,
    expense_snapshot       JSONB         NOT NULL,
    output_snapshot        JSONB         NOT NULL,
    accuracy_status        VARCHAR(32)   NOT NULL,
    calculation_version    VARCHAR(16)   NOT NULL,
    calculated_at          TIMESTAMPTZ   NOT NULL,
    saved_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    visibility_status      VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    archived_at            TIMESTAMPTZ,
    source_history_id      BIGINT        REFERENCES calculation_history(id),
    CONSTRAINT history_type_chk CHECK (calculation_type IN ('PROFIT','PRICE')),
    CONSTRAINT history_visibility_chk CHECK (visibility_status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX history_business_list_idx ON calculation_history (business_id, visibility_status, saved_at DESC, id DESC);

CREATE TABLE subscription_plans (
    code        VARCHAR(32)  PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    version     INTEGER      NOT NULL DEFAULT 1,
    price_uzs   NUMERIC(19,2),
    published   BOOLEAN      NOT NULL DEFAULT false,
    description VARCHAR(500)
);

CREATE TABLE plan_features (
    plan_code    VARCHAR(32)  NOT NULL REFERENCES subscription_plans(code),
    feature      VARCHAR(32)  NOT NULL,
    limit_type   VARCHAR(16)  NOT NULL,
    limit_value  INTEGER,
    PRIMARY KEY (plan_code, feature),
    CONSTRAINT plan_features_limit_chk CHECK (limit_type IN ('LIMIT_APPLIED','UNLIMITED','NOT_APPLICABLE'))
);

CREATE TABLE support_settings (
    id             SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled        BOOLEAN      NOT NULL DEFAULT false,
    contact_label  VARCHAR(80),
    contact_url    VARCHAR(255),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
INSERT INTO support_settings (id, enabled) VALUES (1, false);

CREATE TABLE audit_events (
    id           BIGSERIAL PRIMARY KEY,
    actor_type   VARCHAR(16)  NOT NULL,
    actor_id     VARCHAR(64),
    business_id  BIGINT,
    action       VARCHAR(64)  NOT NULL,
    entity_type  VARCHAR(32),
    entity_id    VARCHAR(64),
    summary      VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_created_idx ON audit_events (created_at DESC);

-- Telegram update_id takror kelsa qayta ishlanmaydi
CREATE TABLE processed_updates (
    update_id     BIGINT       PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- V1.0 Closed Beta: faqat Free. Beta davrida limitlar server konfiguratsiyasida aniq UNLIMITED.
INSERT INTO subscription_plans (code, name, version, price_uzs, published, description)
VALUES ('FREE', 'Free (Beta)', 1, 0, true, 'Yopiq beta davridagi bepul tarif');
INSERT INTO plan_features (plan_code, feature, limit_type, limit_value) VALUES
    ('FREE', 'PROFIT_CALCULATION', 'UNLIMITED', NULL),
    ('FREE', 'PRICE_CALCULATION',  'UNLIMITED', NULL),
    ('FREE', 'PRODUCT_CATALOG',    'UNLIMITED', NULL),
    ('FREE', 'CALCULATION_HISTORY','UNLIMITED', NULL);

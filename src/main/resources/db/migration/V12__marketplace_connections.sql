-- V2: marketplace (Uzum) ulanishi va import qilingan mahsulotlar.

-- Import qilingan mahsulotda tannarx noma'lum bo'lishi mumkin (Uzum bilmaydi) — sotuvchi keyin kiritadi.
ALTER TABLE products ALTER COLUMN unit_cost DROP NOT NULL;

ALTER TABLE products
    ADD COLUMN external_source     VARCHAR(16),
    ADD COLUMN external_product_id BIGINT,
    ADD COLUMN external_sku_id     BIGINT,
    ADD COLUMN external_shop_id    BIGINT,
    ADD COLUMN barcode             VARCHAR(64),
    ADD COLUMN commission_pct      NUMERIC(6,2),
    ADD COLUMN stock_qty           INTEGER,
    ADD COLUMN sold_qty            INTEGER,
    ADD COLUMN returned_qty        INTEGER,
    ADD COLUMN synced_at           TIMESTAMPTZ,
    ADD CONSTRAINT products_external_chk CHECK (external_source IS NULL OR external_source IN ('UZUM', 'FILE')),
    ADD CONSTRAINT products_commission_chk CHECK (commission_pct IS NULL OR (commission_pct >= 0 AND commission_pct <= 100));

CREATE UNIQUE INDEX products_external_sku_uq ON products (business_id, external_source, external_sku_id)
    WHERE external_sku_id IS NOT NULL;
CREATE INDEX products_missing_cost_idx ON products (business_id, id) WHERE unit_cost IS NULL AND status = 'ACTIVE';

-- Sotuvchining marketplace kaliti: faqat AES-256-GCM shifrlangan holda (kalit — SELLERFLOW_SECRETS_KEY, DB'da emas).
CREATE TABLE marketplace_connections (
    business_id      BIGINT       NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    marketplace      VARCHAR(16)  NOT NULL,
    token_ciphertext BYTEA        NOT NULL,
    token_hint       VARCHAR(8)   NOT NULL,
    shop_ids         VARCHAR(500) NOT NULL DEFAULT '',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_sync_at     TIMESTAMPTZ,
    last_sync_count  INTEGER,
    last_error       VARCHAR(200),
    sync_started_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (business_id, marketplace),
    CONSTRAINT marketplace_connections_mp_chk CHECK (marketplace IN ('UZUM')),
    CONSTRAINT marketplace_connections_status_chk CHECK (status IN ('ACTIVE', 'INVALID', 'QUOTA'))
);

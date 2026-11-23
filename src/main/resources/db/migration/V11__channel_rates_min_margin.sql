-- V1+ (TZ 2-modul): har sotuv kanali uchun saqlangan xarajat stavkalari va minimal marja sozlamasi.
CREATE TABLE business_channels (
    business_id BIGINT      NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    channel     VARCHAR(32) NOT NULL,
    expenses    JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (business_id, channel),
    CONSTRAINT business_channels_channel_chk CHECK (channel IN
        ('UZUM','WILDBERRIES','OZON','INSTAGRAM','TELEGRAM_SALES','OFFLINE_STORE','OTHER'))
);

ALTER TABLE businesses ADD COLUMN min_margin_pct NUMERIC(5,2)
    CONSTRAINT businesses_min_margin_chk CHECK (min_margin_pct IS NULL OR (min_margin_pct >= 0 AND min_margin_pct < 100));

-- V3: Uzum moliya — sotuv qatorlari va sotuvchi xarajatlari (davr hisoboti va pul oqimi uchun).

CREATE TABLE marketplace_sales (
    business_id     BIGINT        NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    marketplace     VARCHAR(16)   NOT NULL DEFAULT 'UZUM',
    shop_id         BIGINT        NOT NULL,
    order_id        BIGINT        NOT NULL,
    line_id         BIGINT        NOT NULL,
    product_id      BIGINT,
    sku_id          BIGINT,
    title           VARCHAR(200),
    sell_price      NUMERIC(19,2) NOT NULL DEFAULT 0,
    amount          INTEGER       NOT NULL DEFAULT 0,
    commission      NUMERIC(19,2) NOT NULL DEFAULT 0,
    logistic_fee    NUMERIC(19,2) NOT NULL DEFAULT 0,
    purchase_price  NUMERIC(19,2),
    status          VARCHAR(24)   NOT NULL,
    sold_at         TIMESTAMPTZ   NOT NULL,
    synced_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (business_id, marketplace, shop_id, order_id, line_id)
);
CREATE INDEX marketplace_sales_period_idx ON marketplace_sales (business_id, sold_at);

CREATE TABLE marketplace_expenses (
    business_id   BIGINT        NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    marketplace   VARCHAR(16)   NOT NULL DEFAULT 'UZUM',
    expense_id    BIGINT        NOT NULL,
    shop_id       BIGINT,
    code          VARCHAR(40),
    name          VARCHAR(200),
    source        VARCHAR(80),
    category      VARCHAR(16)   NOT NULL,
    amount        NUMERIC(19,2) NOT NULL,
    direction     VARCHAR(8)    NOT NULL,
    status        VARCHAR(16),
    service_date  TIMESTAMPTZ   NOT NULL,
    synced_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (business_id, marketplace, expense_id),
    CONSTRAINT marketplace_expenses_cat_chk CHECK (category IN
        ('STORAGE','RETURN_STORAGE','FINE','ADS','PHOTO','WITHDRAW_FEE','TRANSFER','OTHER')),
    CONSTRAINT marketplace_expenses_dir_chk CHECK (direction IN ('OUTCOME','INCOME'))
);
CREATE INDEX marketplace_expenses_period_idx ON marketplace_expenses (business_id, service_date);

ALTER TABLE marketplace_connections
    ADD COLUMN finance_synced_at TIMESTAMPTZ,
    ADD COLUMN finance_error     VARCHAR(40);

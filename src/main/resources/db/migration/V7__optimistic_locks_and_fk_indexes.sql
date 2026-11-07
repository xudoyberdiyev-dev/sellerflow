-- LOW (DB): lost-update himoyasi (JPA @Version)
alter table users         add column version bigint not null default 0;
alter table access_grants add column version bigint not null default 0;
alter table admins        add column version bigint not null default 0;

-- LOW (DB): purge/o'chirish yo'lidagi FK'lar uchun indekslar (products/users o'chirilganda seq scan bo'lmasin)
create index if not exists calculation_history_product_idx on calculation_history (product_id) where product_id is not null;
create index if not exists calculation_history_source_idx  on calculation_history (source_history_id) where source_history_id is not null;
create index if not exists calculation_history_user_idx    on calculation_history (user_id);

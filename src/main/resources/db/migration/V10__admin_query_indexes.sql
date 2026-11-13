-- 13-noyabr qayta auditi (LOW): admin dashboard, audit filtrlari va bildirishnomalar uchun indekslar.
create index if not exists users_created_idx on users (created_at);
create index if not exists calculation_history_saved_idx on calculation_history (saved_at);
create index if not exists audit_events_action_id_idx on audit_events (action, id desc);
create index if not exists audit_events_actor_idx on audit_events (lower(actor_id), id desc);
create index if not exists audit_events_business_idx on audit_events (business_id, id desc) where business_id is not null;
create index if not exists audit_events_entity_idx on audit_events (entity_type, entity_id);

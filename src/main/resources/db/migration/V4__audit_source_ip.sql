-- M-6: audit yozuvi qaysi manba IP'dan kelgan so'rov natijasida yaratilgani (admin web so'rovlari uchun).
alter table audit_events add column source_ip varchar(45);
create index audit_events_action_created_idx on audit_events (action, created_at desc);

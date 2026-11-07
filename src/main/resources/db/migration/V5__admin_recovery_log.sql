-- M-7: break-glass tiklash qo'llangan recovery parollarining BCrypt xeshlari —
-- env o'chirilmay qolsa ham har qayta ishga tushishda 2FA qayta-qayta tiklanmasligi uchun.
create table admin_recovery_log (
    id             bigserial primary key,
    admin_id       bigint       not null references admins (id),
    password_hash  varchar(100) not null,
    applied_at     timestamptz  not null default now()
);
create index admin_recovery_log_admin_idx on admin_recovery_log (admin_id);

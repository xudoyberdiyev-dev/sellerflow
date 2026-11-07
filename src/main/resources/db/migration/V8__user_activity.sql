-- D-5: dashboard uchun faollik (faqat sana — tijoriy ma'lumot emas).
-- users.last_seen_at — oxirgi bot faolligi; user_activity_days — kunlik faol foydalanuvchilar va retention.
alter table users add column last_seen_at timestamptz;

create table user_activity_days (
    user_id  bigint not null references users(id) on delete cascade,
    day      date   not null,
    primary key (user_id, day)
);
create index user_activity_days_day_idx on user_activity_days (day);

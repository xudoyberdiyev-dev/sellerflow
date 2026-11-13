-- N-9/N-10/N-11: akkaunt ma'lumotini to'liq tozalash belgisi va o'chirish so'rovidan oldingi holat.
alter table users add column purged_at timestamptz;
alter table users add column status_before_deletion varchar(30);
-- Avval (eski versiyada) psevdonimlangan yozuvlar allaqachon tozalangan deb belgilanadi
update users set purged_at = coalesce(updated_at, now()) where telegram_user_id < 0 and status = 'CLOSED';
create index users_status_updated_idx on users (status, updated_at);

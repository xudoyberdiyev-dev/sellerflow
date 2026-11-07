-- M-1: TOTP replay himoyasi — oxirgi muvaffaqiyatli ishlatilgan 30 soniyalik qadam.
alter table admins add column totp_last_step bigint;

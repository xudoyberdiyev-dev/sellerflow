-- LOW (maxfiylik): shartlar versiyasi matn xeshiga bog'lanadi — matn o'zgarib versiya o'zgarmasa ilova ishga tushmaydi.
create table terms_versions (
    version        varchar(64)  primary key,
    text_sha256    char(64)     not null,
    first_seen_at  timestamptz  not null default now()
);

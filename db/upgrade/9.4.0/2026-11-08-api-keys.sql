-- Free registered API keys, step 6 PR 2 (decided by Brian Fisher 2026-11-07 and 2026-11-08).
--
-- api_key: one row per key. The key itself is never stored: only its SHA-256 (key_hash) and its first characters for
-- display (prefix, e.g. "aw_3f9c"). A registration starts as 'pending' until the email link is followed
-- (confirm_token_hash, also only a hash), then 'active'; an admin can set 'revoked'. Limits per key: NULL = the
-- level's default (registered: 60 a minute, 5,000 a day, 500 records an answer, 50,000 records a day; collaborator:
-- set per key). Brian sees every registration and its use on the admin page (PR 5).
-- api_usage_daily: requests, records and refusals (HTTP 429) per key and day; key_id 0 = all requests without a key.
--
-- Created by tools/api/setup-usage-login.sh (with the database login that may write only these two tables), or by
-- hand with this file. Rollback: drop table api_usage_daily; drop table api_key; (nothing else uses them yet).

create table if not exists api_key (
  id int not null auto_increment,
  prefix varchar(16) not null,
  key_hash char(64) not null,
  name varchar(200) not null,
  email varchar(254) not null,
  institution varchar(300) null,
  project text not null,
  level varchar(16) not null default 'registered',
  status varchar(16) not null default 'pending',
  per_minute int null,
  per_day int null,
  max_records int null,
  records_per_day int null,
  confirm_token_hash char(64) null,
  confirm_expires datetime null,
  created datetime not null default current_timestamp,
  confirmed datetime null,
  last_used datetime null,
  revoked datetime null,
  note text null,
  primary key (id),
  unique key api_key_hash (key_hash),
  unique key api_key_prefix (prefix),
  key api_key_email (email)
);

create table if not exists api_usage_daily (
  day date not null,
  key_id int not null,
  requests int not null default 0,
  records int not null default 0,
  limited int not null default 0,
  primary key (day, key_id)
);

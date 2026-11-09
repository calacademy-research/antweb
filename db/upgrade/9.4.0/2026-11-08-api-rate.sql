-- Free registered API keys, step 6 PR 3 (decided by Brian Fisher 2026-11-08): the short-lived counters of the API v4
-- limits, shared by the three gunicorn workers (api/v3.1/api_keys.py).
--
-- api_rate: one row per caller and period. bucket = 'key:<api_key.id>' for a key, or 'ip:<SHA-256 of the IP with a
-- salt that changes every day>' for a visitor without a key (the IP itself is never stored). period 'm' = the minute
-- that starts at `start` (requests a minute, keys and visitors), 'd' = the UTC day that starts at `start` (requests
-- and records a day of visitors without a key; keys count their days in api_usage_daily). The API deletes rows older
-- than two days (index api_rate_start).
--
-- Created by tools/api/setup-usage-login.sh (which also lets the usage login antweb_api_usage select, insert, update
-- and delete rows of this table only), or by hand with this file. Rollback: drop table api_rate; (only the v4 limits
-- use it; v4 then answers 503).

create table if not exists api_rate (
  bucket varchar(80) not null,
  period char(1) not null,
  start datetime not null,
  requests int not null default 0,
  records int not null default 0,
  primary key (bucket, period, start),
  key api_rate_start (start)
);

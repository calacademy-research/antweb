#!/usr/bin/env bash
# Step 6, API keys PR 2 and PR 3 (2026-11-08): creates the API key tables and the database login that may write ONLY
# those tables, on the server it runs on (staging or production). Safe to run again (PR 3 adds api_rate to a login
# made by PR 2). Run as root in the project dir's parent:
#   bash /root/antweb/tools/api/setup-usage-login.sh            (production)
#   bash /root/antweb-next/tools/api/setup-usage-login.sh       (staging)
# - creates api_key and api_usage_daily (db/upgrade/9.4.0/2026-11-08-api-keys.sql) and api_rate, the per-minute and
#   per-visitor counters of the v4 limits (db/upgrade/9.4.0/2026-11-08-api-rate.sql); both safe to run again
# - creates MySQL user antweb_api_usage with only what the API needs (least privilege, reapplied exactly on every run:
#   all of the login's privileges are revoked first, then granted again, so a broader grant from an earlier run is
#   removed):
#     api_key:          SELECT on id, key_hash, level, status, per_minute, per_day, max_records, records_per_day and
#                       last_used (the key lookup, and the WHERE of the last_used update), UPDATE on last_used only
#     api_usage_daily:  SELECT, INSERT, UPDATE (the daily counts)
#     api_rate:         SELECT, INSERT, UPDATE, DELETE (the API deletes its rows older than two days)
#   It cannot read names, emails or anything else in the database. The web application (registration, admin page)
#   writes api_key with its own login.
# - with a random password generated here and never printed (nor put on a command line: it goes to MySQL on stdin);
#   adds usageUser= and usagePassword= to etc/api-db.properties (the API's own settings file, already mounted in the
#   api container), after a backup etc/api-db.properties.bak-<date>. When the script writes a new password it also
#   sets it on the MySQL login (alter user); if the file has usageUser already, its password is kept and set on the
#   login (made again if it was dropped). So the file and MySQL always agree.
# Then restart the API so it reads the login: docker restart antweb_api_1 (v4 answers 503 "not configured" without it).
# Rollback: drop user 'antweb_api_usage'@'%'; restore etc/api-db.properties from the backup; docker restart antweb_api_1.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/../.." && pwd)"
ETC="$DIR/etc"; F="$ETC/db.properties"; P="$ETC/api-db.properties"
[ -f "$F" ] || { echo "no $F"; exit 1; }
[ -f "$P" ] || { echo "no $P (the API's read-only login file)"; exit 1; }
export MYSQL_PWD="$(sed -n 's/^password=//p' "$F")"
DBUSER="$(sed -n 's/^user=//p' "$F")"
q() { docker exec -i -e MYSQL_PWD antweb_mysql_1 mysql -u "$DBUSER" ant "$@"; }

q < "$DIR/db/upgrade/9.4.0/2026-11-08-api-keys.sql"
q < "$DIR/db/upgrade/9.4.0/2026-11-08-api-rate.sql"
echo "tables: $(q -N -e "select group_concat(table_name order by table_name) from information_schema.tables where table_schema='ant' and table_name in ('api_key','api_usage_daily','api_rate')")"

if grep -q '^usageUser=' "$P"; then
  # the login gets the password in the file (made if it was dropped), so the file and MySQL agree
  PW="$(sed -n 's/^usagePassword=//p' "$P")"
  [ -n "$PW" ] || { echo "etc/api-db.properties has usageUser but no usagePassword"; exit 1; }
  q <<SQL
create user if not exists 'antweb_api_usage'@'%' identified by '$PW';
alter user 'antweb_api_usage'@'%' identified by '$PW';
SQL
  unset PW
  echo "etc/api-db.properties already has usageUser: its password kept and set on the login"
else
  PW="$(openssl rand -base64 36 | tr -dc 'A-Za-z0-9' | head -c 32)"
  [ ${#PW} -eq 32 ] || { echo "password generation failed"; exit 1; }
  q <<SQL
create user if not exists 'antweb_api_usage'@'%' identified by '$PW';
alter user 'antweb_api_usage'@'%' identified by '$PW';
SQL
  cp -p "$P" "$P.bak-$(date +%Y%m%d)"
  printf 'usageUser=antweb_api_usage\nusagePassword=%s\n' "$PW" >> "$P"
  unset PW
  echo "login antweb_api_usage ready with a new password; usageUser/usagePassword added to etc/api-db.properties (backup $P.bak-$(date +%Y%m%d))"
fi
# Exactly these privileges, on every run (revoke all first: a re-run removes broader grants from earlier versions of
# this script, e.g. PR 2's table-wide select, insert, update on api_key). Revoking all of a login's privileges never
# fails, also when it holds none. Both statements in one session, so the API is without its grants only for a moment.
q <<SQL
revoke all privileges, grant option from 'antweb_api_usage'@'%';
grant select (id, key_hash, level, status, per_minute, per_day, max_records, records_per_day, last_used), update (last_used) on ant.api_key to 'antweb_api_usage'@'%';
grant select, insert, update on ant.api_usage_daily to 'antweb_api_usage'@'%';
grant select, insert, update, delete on ant.api_rate to 'antweb_api_usage'@'%';
SQL
echo "grants: $(q -N -e "show grants for 'antweb_api_usage'@'%'" | grep -c 'api_key\|api_usage_daily\|api_rate') on the three tables (expect 3), $(q -N -e "show grants for 'antweb_api_usage'@'%'" | grep -vc 'api_key\|api_usage_daily\|api_rate\|^GRANT USAGE ON \*\.\*') others (expect 0)"
# The self-check logs in as the usage login. Its password goes to the mysql client as an option file on stdin, never on
# a command line (visible in ps) and never printed.
uq() { printf '[client]\npassword=%s\n' "$(sed -n 's/^usagePassword=//p' "$P")" | docker exec -i antweb_mysql_1 mysql --defaults-extra-file=/dev/stdin -h 127.0.0.1 -u antweb_api_usage ant -N -e "$1" 2>&1; }
echo "check: $(uq "select count(*) from api_key where status = 'active'" | tail -1) active keys; the login cannot read specimen: $(uq "select 1 from specimen limit 1" | grep -c 'denied') (expect 1), nor api_key.email: $(uq "select email from api_key limit 1" | grep -c 'denied') (expect 1)"

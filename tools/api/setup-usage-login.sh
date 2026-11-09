#!/usr/bin/env bash
# Step 6, API keys PR 2 and PR 3 (2026-11-08): creates the API key tables and the database login that may write ONLY
# those tables, on the server it runs on (staging or production). Safe to run again (PR 3 adds api_rate to a login
# made by PR 2). Run as root in the project dir's parent:
#   bash /root/antweb/tools/api/setup-usage-login.sh            (production)
#   bash /root/antweb-next/tools/api/setup-usage-login.sh       (staging)
# - creates api_key and api_usage_daily (db/upgrade/9.4.0/2026-11-08-api-keys.sql) and api_rate, the per-minute and
#   per-visitor counters of the v4 limits (db/upgrade/9.4.0/2026-11-08-api-rate.sql); both safe to run again
# - creates MySQL user antweb_api_usage with SELECT, INSERT, UPDATE on api_key and api_usage_daily, and SELECT, INSERT,
#   UPDATE, DELETE on api_rate (the API deletes its rows older than two days), and nothing else, with a random
#   password generated here and never printed; the api_rate grant is (re)applied on every run
# - adds usageUser= and usagePassword= to etc/api-db.properties (the API's own settings file, already mounted in the
#   api container), after a backup etc/api-db.properties.bak-<date>; if the login exists already, nothing changes
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
  echo "etc/api-db.properties already has usageUser: login left as it is"
else
  PW="$(openssl rand -base64 36 | tr -dc 'A-Za-z0-9' | head -c 32)"
  [ ${#PW} -eq 32 ] || { echo "password generation failed"; exit 1; }
  q <<SQL
create user if not exists 'antweb_api_usage'@'%' identified by '$PW';
grant select, insert, update on ant.api_key to 'antweb_api_usage'@'%';
grant select, insert, update on ant.api_usage_daily to 'antweb_api_usage'@'%';
SQL
  cp -p "$P" "$P.bak-$(date +%Y%m%d)"
  printf 'usageUser=antweb_api_usage\nusagePassword=%s\n' "$PW" >> "$P"
  unset PW
  echo "login antweb_api_usage created; usageUser/usagePassword added to etc/api-db.properties (backup $P.bak-$(date +%Y%m%d))"
fi
q <<SQL
grant select, insert, update, delete on ant.api_rate to 'antweb_api_usage'@'%';
SQL
echo "grants: $(q -N -e "show grants for 'antweb_api_usage'@'%'" | grep -c 'api_key\|api_usage_daily\|api_rate') on the three tables (expect 3)"
echo "check: $(docker exec -e MYSQL_PWD="$(sed -n 's/^usagePassword=//p' "$P")" antweb_mysql_1 mysql -h 127.0.0.1 -u antweb_api_usage ant -N -e "select count(*) from api_key" 2>&1 | tail -1) keys; the login cannot read specimen: $(docker exec -e MYSQL_PWD="$(sed -n 's/^usagePassword=//p' "$P")" antweb_mysql_1 mysql -h 127.0.0.1 -u antweb_api_usage ant -N -e "select 1 from specimen limit 1" 2>&1 | grep -c 'denied')"

#!/usr/bin/env bash
#
# Usage: sh db-monthly-dump.sh [dbname]
#
# Backup of all antweb databases
#

[ -n "$BASH_VERSION" ] || exec bash "$0" "$@"   # also when started as "sh script": pipefail needs bash

dbname="ant"
date=$(date +"%Y-%m-%d")

echo 'Making monthly database backup of db:' $dbname

dbuser="antweb"
# MySQL login from the server-only etc/db.properties (never in git).
dbconf=""
for f in /antweb/deploy/etc/db.properties "$(dirname "$0")/../etc/db.properties" /root/antweb/etc/db.properties; do
  [ -f "$f" ] && dbconf="$f" && break
done
[ -n "$dbconf" ] || { echo "db.properties not found" >&2; exit 1; }
MYSQL_PWD=$(sed -n 's/^password=//p' "$dbconf")
export MYSQL_PWD
#backupdir="/data/antweb/web/db"
#backupdir="/mnt/backup/db"
# The backup mount itself, not /data/antweb/backup: /data/antweb is owned by
# tomcat, and this runs as root.
backupdir="/mnt/backup/db/monthly"

datedBackupFile=$backupdir/"$date".sql.gz

set -o pipefail   # a mysqldump that fails part way fails the whole pipeline

if [ ! -d "$backupdir" ]; then
  echo "Error: $backupdir is missing (/mnt/backup not mounted?)" >&2
  exit 1
fi
tempfile=$(mktemp /tmp/monthly-dump.XXXXXX) || exit 1
if ! mysqldump --skip-lock-tables -u$dbuser -h mysql --databases "$dbname" --routines --single-transaction --quick --no-tablespaces --default-character-set=utf8mb4 | gzip -c -9 > "$tempfile"; then
  echo "Error: mysqldump failed; nothing written to $backupdir" >&2
  rm -f "$tempfile"
  exit 1
fi
# A complete dump: at least 1 KB, a valid gzip, and mysqldump's last line "-- Dump completed".
if [ $(stat -c %s "$tempfile") -lt 1024 ] || ! gzip -t "$tempfile" 2>/dev/null \
   || ! gzip -dc "$tempfile" | tail -n 1 | grep -q '^-- Dump completed'; then
  echo "Error: the dump is incomplete; nothing written to $backupdir" >&2
  rm -f "$tempfile"
  exit 1
fi
if ! mv "$tempfile" "$datedBackupFile"; then
  echo "Error: could not write $datedBackupFile" >&2
  rm -f "$tempfile"
  exit 1
fi
echo "Monthly dump written: $datedBackupFile ($(stat -c %s "$datedBackupFile") bytes)"
# keep monthly backups for a year (only after a good dump)
find "$backupdir" -mindepth 1 -mtime +365 -type f -name "*.sql.gz" -delete

# End of script db_monthly_dump.sh

#!/usr/bin/env bash
# Usage: sh db-daily-dump.sh [dbname]
#
# Backup all databases
#
# Assumes that /data/antweb/backup/db/daily is world writable

date=$(date +"%Y-%m-%d")

# One line for the app's hourly mount check (MountCheck, 2026-10-24): "ok <time> <bytes> <file>" or
# "failed <time> <reason>", in the web log folder (host logs/web = the container's web/log; not served).
# The app cannot read /mnt/backup itself (the rclone mount is root-only), so it reads this line instead,
# and a line older than 26 hours means the dump did not run.
statusfile="$(cd "$(dirname "$0")/.." && pwd)/logs/web/db-dump-status.txt"
status() {
  [ -d "$(dirname "$statusfile")" ] || return 0
  echo "$1 $(date '+%Y-%m-%dT%H:%M:%S%z') $2" > "$statusfile.tmp" && mv -f "$statusfile.tmp" "$statusfile"
}

dbname="ant"
if [ $1 ]; then
  dbname="$1"
fi

dbuser="antweb"
# MySQL login from the server-only etc/db.properties (never in git).
dbconf=""
for f in /antweb/deploy/etc/db.properties "$(dirname "$0")/../etc/db.properties" /root/antweb/etc/db.properties; do
  [ -f "$f" ] && dbconf="$f" && break
done
[ -n "$dbconf" ] || { echo "db.properties not found" >&2; status failed "db.properties not found"; exit 1; }
MYSQL_PWD=$(sed -n 's/^password=//p' "$dbconf")
export MYSQL_PWD
rootbackupdir="/mnt/backup/db"
backupdir="/mnt/backup/db/daily"

curBak="$rootbackupdir"/"$dbname"-currentDump.sql.gz
tempfile=/tmp/"$date".sql.gz
datedBackupFile=$backupdir/"$date".sql.gz

if [ -d $backupdir ]; then
  docker exec -e MYSQL_PWD antweb_mysql_1 mysqldump -u $dbuser --routines --skip-lock-tables --no-tablespaces --default-character-set=utf8mb4 --databases "$dbname" | tail -n +2 | gzip -c -9 > "$tempfile"

  # Check if the tempfile is missing, zero-length, or less than 1K
  if [ ! -f "$tempfile" ] || [ ! -s "$tempfile" ] || [ $(stat -c %s "$tempfile") -lt 1024 ]; then
    echo "Error: The backup file is either missing, zero-length, or less than 1K. Exiting." >> "$backupdir"/error.log
    status failed "the dump is missing or under 1 KB"
    exit 1
  fi
  
  if ! mv "$tempfile" "$datedBackupFile"; then
    status failed "could not write $datedBackupFile"
    exit 1
  fi
  status ok "$(stat -c %s "$datedBackupFile") $datedBackupFile"
  echo "Copying $datedBackupFile to $curBak"
  cp "$datedBackupFile" "$curBak"
else
  status failed "$backupdir is missing (/mnt/backup not mounted?)"
fi

# Remove backups after a week
find "$backupdir" -mindepth 1 -mtime +7 -type f -name "*.sql.gz" -delete

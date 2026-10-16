#!/usr/bin/env bash
# Usage: sh db-daily-dump.sh [dbname]
#
# Backup all databases
#
# Assumes that /data/antweb/backup/db/daily is world writable

date=$(date +"%Y-%m-%d")

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
[ -n "$dbconf" ] || { echo "db.properties not found" >&2; exit 1; }
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
    exit 1
  fi
  
  mv "$tempfile" "$datedBackupFile"
  echo "Copying $datedBackupFile to $curBak"
  cp "$datedBackupFile" "$curBak"
fi

# Remove backups after a week
find "$backupdir" -mindepth 1 -mtime +7 -type f -name "*.sql.gz" -delete

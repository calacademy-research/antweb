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
dbpass="f0rm1c6"
rootbackupdir="/mnt/backup/db"
backupdir="/mnt/backup/db/daily"

curBak="$rootbackupdir"/"$dbname"-currentDump.sql.gz
tempfile=/tmp/"$date".sql.gz
datedBackupFile=$backupdir/"$date".sql.gz

if [ -d $backupdir ]; then
  docker exec antweb_mysql_1 mysqldump -u $dbuser --password=$dbpass --routines  --skip-lock-tables --default-character-set=utf8 --all-databases | tail -n +2 | gzip -c -9 > "$tempfile"

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

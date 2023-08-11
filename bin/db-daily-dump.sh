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
#backupdir="/data/antweb/web/db"
#backupdir="/mnt/backup/db"
# /mnt/antweb/backup/db/daily/
# /mnt/backup/db/daily/
rootbackupdir="/mnt/backup/db"
backupdir="/mnt/backup/db/daily"

curBak="$rootbackupdir"/"$dbname"-currentDump.sql.gz

tempfile=/tmp/"$date".sql.gz

datedBackupFile=$backupdir/"$date".sql.gz

if [ -d $backupdir ]; then
  docker exec -it antweb_mysql_1 mysqldump -u $dbuser --password=$dbpass --routines  --skip-lock-tables --default-character-set=utf8 --all-databases --ignore-database 
sys | gzip -c -9 > "$tempfile"
#  mysqldump --skip-lock-tables -u$dbuser -p$dbpass -h mysql --all-databases --routines --single-transaction --quick --ignore-database sys | gzip -c -9 > "$tempfile"
  mv "$tempfile" "$datedBackupFile"
  echo "Copying $datedBackupFile to $curBak"
  cp "$datedBackupFile" "$curBak"
fi

# Remove backups after a week
find "$backupdir" -mindepth 1 -mtime +7 -type f -name "*.sql.gz" -delete

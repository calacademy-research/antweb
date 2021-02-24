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
rootbackupdir="/data/antweb/backup/db"
backupdir="/data/antweb/backup/db/daily"

curBak="$rootbackupdir"/"$dbname"-currentDump.sql.gz

datedBackupFile=$backupdir/backup-"$date".sql.gz

if [ -d $backupdir ]; then
  mysqldump --skip-lock-tables -u$dbuser -p$dbpass --all-databases --routines --single-transaction --quick | gzip > "$datedBackupFile"
  echo "Copying $datedBackupFile to $curBak"
  cp "$datedBackupFile" "$curBak"
fi

#if [ $dbname == "ant" ] || [ $dbname == "stage" ] ; then
# This may break on a mac...
maxDate=$(date --date="7 days ago" +"%Y-%m-%d")
#echo "Linux date command - 7 days ago:" $dayofweek
#else
#dayofweek=`date -v -6d +%a`
#echo "BSD date command - 6 days ago:" $dayofweek
#fi

for fn in "$backupdir"/*; do
  fdate="${fn#*backup-}"   # remove everything in filename before "backup-"
  fdate="${fdate%.sql.gz}"    # remove .sql.gz from end of filename, leaving only the date

  if [[ "$fdate" > "$maxDate" ]]; then
    echo "Found backup to be removed $fn"
#    rm "$fn"
  fi
done

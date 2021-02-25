#
# Usage: sh db-monthly-dump.sh [dbname]
#
# Backup of all antweb databases
#

dbname="ant"
date=$(date +"%Y-%m-%d")

echo 'Making monthly database backup of db:' $dbname

dbuser="antweb"
dbpass="f0rm1c6"
#backupdir="/data/antweb/web/db"
#backupdir="/mnt/backup/db"
backupdir="/data/antweb/backup/db/monthly"

tempfile=/tmp/"$date".sql.gz

datedBackupFile=$backupdir/"$date".sql.gz

if [ -d $backupdir ]; then
  mysqldump --skip-lock-tables -u$dbuser -p$dbpass --all-databases --routines --single-transaction --quick | gzip -c -9 > "$tempfile"
  mv "$tempfile" "$datedBackupFile"
fi
# remove dump from 3 months ago - so that we only have 3 months at a time
find "$backupdir" -mindepth 1 -mtime +100 -type f -name "*.sql.gz" -delete

# End of script db_monthly_dump.sh

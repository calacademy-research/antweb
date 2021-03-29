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
  mysqldump --skip-lock-tables -u$dbuser -p$dbpass -h mysql --all-databases --routines --single-transaction --quick --ignore-database sys | gzip -c -9 > "$tempfile"
  mv "$tempfile" "$datedBackupFile"
fi
# keep monthly backups for a year
find "$backupdir" -mindepth 1 -mtime +365 -type f -name "*.sql.gz" -delete

# End of script db_monthly_dump.sh

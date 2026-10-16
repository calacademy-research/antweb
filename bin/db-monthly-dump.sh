#
# Usage: sh db-monthly-dump.sh [dbname]
#
# Backup of all antweb databases
#

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
backupdir="/data/antweb/backup/db/monthly"

tempfile=/tmp/"$date".sql.gz

datedBackupFile=$backupdir/"$date".sql.gz

if [ -d $backupdir ]; then
  mysqldump --skip-lock-tables -u$dbuser -h mysql --databases "$dbname" --routines --single-transaction --quick --no-tablespaces --default-character-set=utf8mb4 | gzip -c -9 > "$tempfile"
  mv "$tempfile" "$datedBackupFile"
fi
# keep monthly backups for a year
find "$backupdir" -mindepth 1 -mtime +365 -type f -name "*.sql.gz" -delete

# End of script db_monthly_dump.sh

#
# Usage: sh db-monthly-dump.sh [dbname]
#
# Backup the antweb, or specified, database
# 
# Assumes that the /data/db is world writable
monthofyear=`date +%b`
dbname="ant"
if [ $1 ] ; then
dbname="$1"
fi

echo 'Making monthly database backup of db:' $dbname

dbuser="antweb"
dbpass="f0rm1c6"
#backupdir="/data/antweb/web/db"
#backupdir="/mnt/backup/db"
backupdir="/data/antweb/backup/db"

if [ -d $backupdir ]; then
mysqldump --opt -u$dbuser -p$dbpass -B $dbname | gzip > $backupdir/backup-$dbname-$monthofyear.sql.gz
fi

# remove dump from 3 months ago - so that we only have 3 months at a time
monthofyear=`date --date="3 months ago" +%b`
olddump=$backupdir/backup-$dbname-$month.sql.gz
if [ -e  $olddump ]; then
rm $olddump
fi

# End of script db_monthly_dump.sh

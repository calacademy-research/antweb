#
# Usage: sh db-daily-dump.sh [dbname]
#
# Backup the antweb, or specified, database
# 
# Assumes that the /data/db is world writable

dayofweek=`date +%a`

dbname="ant"
if [ $1 ] ; then
dbname="$1"
fi

echo 'Making daily database backup of db:' $dbname

dbuser="antweb"
dbpass="f0rm1c6"
#backupdir="/data/antweb/web/db"
#backupdir="/mnt/backup/db"
backupdir="/data/antweb/backup/db"
 
curBak=$backupdir/$dbname-currentDump.sql.gz

if [ -d $backupdir ]; then
mysqldump --skip-lock-tables -u$dbuser -p$dbpass --all-databases --routines --single-transaction --quick | gzip > $backupdir/backup-$dbname-$dayofweek.sql.gz
echo 'Copying ' $backupdir/backup-$dbname-$dayofweek.sql.gz ' to ' $curBak
cp $backupdir/backup-$dbname-$dayofweek.sql.gz $curBak
fi

echo 'db:' $dbname

#if [ $dbname == "ant" ] || [ $dbname == "stage" ] ; then
# This may break on a mac...
dayofweek=`date --date="6 days ago" +%a`
echo "Linux date command - 6 days ago:" $dayofweek
#else
#dayofweek=`date -v -6d +%a`
#echo "BSD date command - 6 days ago:" $dayofweek
#fi

olddump=$backupdir/backup-$dbname-$dayofweek.sql.gz
if [ -e  $olddump ]; then
echo 'Removing ' $olddump
rm $olddump
else
echo "Not removing old daily backup (does not exist):" $olddump
fi

# End of script db_daily_dump.sh

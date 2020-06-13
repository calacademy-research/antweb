#
# Usage: sh bin/srcBackup.sh [release]
#
# Backup the source tree
# 
# Writes to /antewb/bak/rel a file like antweb8.34.zip


rel="rel"
if [ $1 ] ; then
rel="$1"
fi

fileName="antweb"
ext=".zip"

backupDir="/antweb/bak/rel"

fileDir=$backupDir/$fileName$rel$ext

echo 'Making daily database backup of db:' $fileDir


if [ -d $backupDir ]; then
ant clean
zip -r $fileDir .
fi

# End of script

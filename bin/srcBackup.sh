#
# Usage: sh bin/srcBackup.sh [release]
#
# Backup the source tree
# 
# Writes to /antewb/bak/rel a file like antweb8.34.zip


rel="rel"
if [ $1 ] ; then
rel="$1"
else
  echo "Which release number?"
  echo "  ex: sh bin/srcBackup.sh 8.35"
  echo ""
  exit
fi

fileName="antweb"
ext=".zip"

backupDir="/antweb/bak/rel"

fileDir=$backupDir/$fileName$rel$ext

echo 'Backing up source to:' $fileDir

if [ -d $backupDir ]; then
zip -r --exclude=".git/*" --exclude="build/*" $fileDir .
fi

echo ""

# End of script

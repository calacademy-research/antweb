
# This file is useful, on production and stage, for creating a set of handy softlinks to access
# resoures.  Recommended, in a directory called links, execute this script, like:
#
# See also linksDocker.sh, to be used similarly from within docker:
# docker-compose exec antweb bash
#
# From within a manually created links directory
# Create by running something like:
# sh ../antweb/antweb/bin/links.sh

rm *

ln -s /mnt mnt
ln -s mnt/backup/db dbBackup

ln -s mnt/antweb/web/ webMnt
ln -s mnt/antweb/web/bak/taxonSets taxonSetsBak


#ln -s /usr/local/tomcat tomcat
#ln -s tomcat/logs/antweb.log antweb.log
#ln -s tomcat/logs/antwebInfo.log antwebInfo.log
#ln -s tomcat/logs/appCheck.log appCheck.log
#ln -s /usr/local/apache/logs/access_log access_log
#ln -s /usr/local/apache/logs/error_log error_log
#ln -s /var/www/html/imageUpload/ imageUpload
#ln -s /home/antweb/workingdir workingdir

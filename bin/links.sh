
# This file is useful, on production and stage, for creating a set of handy softlinks to access
# resoures.  Recommended, in a directory called links, execute this script, like:
#
# sh /home/antweb/antweb/bin/links.sh

ln -s /usr/local/tomcat tomcat
ln -s tomcat/logs/antweb.log antweb.log
ln -s tomcat/logs/appCheck.log appCheck.log
ln -s /usr/local/apache/logs/access_log access_log
ln -s /usr/local/apache/logs/error_log error_log
ln -s /var/www/html/imageUpload/ imageUpload
ln -s /home/antweb/workingdir workingdir

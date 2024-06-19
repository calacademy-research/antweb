
# This file is useful, on production and stage, for creating a set of handy softlinks to access
# resoures.  Recommended, in a directory called links, execute this script, like:
#
# sh /home/antweb/antweb/bin/links.sh


rm *

#ln -s /usr/local/apache/logs/access_log access_log
ln -s /usr/local/tomcat tomcat
ln -s tomcat/logs/antweb.log antweb.log
ln -s tomcat/logs/antwebInfo.log antwebInfo.log
#ln -s tomcat/logs/appCheck.log appCheck.log
#ln -s /var/www/html/imageUpload/ imageUpload
#ln -s /home/antweb/workingdir workingdir



#lrwxrwxrwx 1 antweb antweb   33 Jun 17 18:16 access_log -> /usr/local/apache/logs/access_log
#lrwxrwxrwx 1 antweb antweb   22 Jun 17 18:16 antweb.log -> tomcat/logs/antweb.log
#lrwxrwxrwx 1 root   root     26 Jun 19 00:52 antwebInfo.log -> tomcat/logs/antwebInfo.log
#lrwxrwxrwx 1 antweb antweb   24 Jun 17 18:16 appCheck.log -> tomcat/logs/appCheck.log
#lrwxrwxrwx 1 antweb antweb   32 Jun 17 18:16 error_log -> /usr/local/apache/logs/error_log
#lrwxrwxrwx 1 antweb antweb   26 Jun 17 18:16 imageUpload -> /var/www/html/imageUpload/
#lrwxrwxrwx 1 antweb antweb   17 Jun 17 18:16 tomcat -> /usr/local/tomcat
#lrwxrwxrwx 1 antweb antweb   23 Jun 17 18:16 workingdir -> /home/antweb/workingdir
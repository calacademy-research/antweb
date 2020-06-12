# Execute from within the links directory
# sudo sh ../deploy/etc/makeLinks.sh

ln -sf /antweb/logs antwebLogs 

ln -sf /usr/local/tomcat/ tomcat
ln -sf tomcat/logs tomcatLogs 
ln -sf tomcat/logs/detail detailLogs
ln -sf tomcat/logs/antweb.log antweb.log
ln -sf tomcat/logs/antwebInfo.log antwebInfo.log
ln -sf tomcat/logs/catalina.out catalina.out
ln -sf tomcat/work/Catalina/localhost/ROOT/org/apache/jsp/ jsp

ln -sf /var/log/mysqld.log mysqld.log

ln -sf /var/log/httpd/ apache
ln -sf apache/access_log access_log
ln -sf apache/ssl_access_log ssl_access_log
ln -sf apache/error_log error_log

ln -sf /mnt/backup/db/ant-currentDump.sql.gz currentDump.sql.gz
ln -sf /var/www/html/ webroot
ln -sf /data/antweb/web/log webLogs

#ln -sf /antweb/log/appCheck.log appCheck.log
#ln -sf tomcat/logs/appCheck.log appCheck.log
#ln -sf tomcat/logs/urlCheck.log urlCheck.log
#ln -sf tomcat/logs/processList.log processList.log
#ln -sf tomcat/work/Catalina/localhost/_/org/apache/jsp/ jsp
#ln -sf /var/www/html/imageUpload/ imageUpload
#ln -sf /home/antweb/workingdir workingdir
#ln -sf /etc/httpd/logs/ oldApache
#ln -sf /var/log/php-fpm/error.log php_log


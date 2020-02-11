# admin.sh was executed by the Antweb app at the end of the scheduled tasks.
# Now it is a crontab:
# 0 21 * * * /home/antweb/antweb_deploy/bin/admin.sh > /home/antweb/adminTask.log

sh unclosedConnections.sh

sh /home/antweb/antweb_deploy/bin/db-daily-dump.sh &
sh /home/antweb/antweb_deploy/bin/db-monthly-dump.sh &
sh /home/antweb/antweb_deploy/bin/db-daily-dump.sh movable &
sh /home/antweb/antweb_deploy/bin/db-monthly-dump.sh movable &

wait 

cp /mnt/backup/db/ant-currentDump.sql.gz /data/antweb/web/db/ant-currentDump.sql.gz

rm /var/log/mysqld.log
rm /var/log/httpd22/access_log-*
rm /var/log/mysqld.log*
rm /data/antweb/web/log/accessLog.txt
rm /usr/local/tomcat/logs/host-manager.2*
rm /usr/local/tomcat/logs/localhost.2*
rm /usr/local/tomcat/logs/catalina.2*
rm /usr/local/tomcat/logs/localhost_access_log.2*
rm /usr/local/tomcat/logs/manager.2*
rm /var/log/mysql/slow.log

# 11G
# rm /root/mysqld.log.local  

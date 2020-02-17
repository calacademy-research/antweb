# admin.sh was executed by the Antweb app at the end of the scheduled tasks.
# Now it is a crontab:
# 0 21 * * * /antweb/antweb_deploy/bin/admin.sh > /antweb/log/adminTask.log

sh unclosedConnections.sh

sh /antweb/antweb_deploy/bin/db-daily-dump.sh &
sh /antweb/antweb_deploy/bin/db-monthly-dump.sh &

wait 

cp /mnt/backup/db/ant-currentDump.sql.gz /data/antweb/web/db/ant-currentDump.sql.gz

#rm /var/log/mysqld.log
#rm /var/log/apache2/access.log.*

# Gets big.
#rm -rf /data/antweb/web/log/bak/2018

#rm /usr/local/tomcat/logs/host-manager.2*
#rm /usr/local/tomcat/logs/localhost.2*
#rm /usr/local/tomcat/logs/catalina.2*
#rm /usr/local/tomcat/logs/localhost_access_log.2*
#rm /usr/local/tomcat/logs/manager.2*

#rm /var/log/mysql/slow.log

# 11G
# rm /root/mysqld.log.local  

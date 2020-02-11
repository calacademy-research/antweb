# This is similar to reboot.sh. Derived from it.
#
# 5 1 * * * sh /home/antweb/antweb_deploy/bin/reboot.sh

# /etc/init.d/priv_tomcat stop
sh /home/antweb/antweb_deploy/bin/db-daily-dump.sh &
sh /home/antweb/antweb_deploy/bin/db-monthly-dump.sh &
sh /home/antweb/antweb_deploy/bin/db-daily-dump.sh movable &
sh /home/antweb/antweb_deploy/bin/db-monthly-dump.sh movable &

wait 

cp /dev/null /var/log/mysqld.log
rm /var/log/mysqld.log*
cp /dev/null /data/antweb/web/log/accessLog.txt
rm /usr/local/tomcat/logs/host-manager.2*
rm /usr/local/tomcat/logs/localhost.2*
rm /usr/local/tomcat/logs/catalina.2*
rm /usr/local/tomcat/logs/localhost_access_log.2*
rm /usr/local/tomcat/logs/manager.2*

wget https://www.antweb.org/schedule.do?action=run

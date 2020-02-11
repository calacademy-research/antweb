# This is run as a cron job, as such:
#
# 5 1 * * * sh /home/antweb/antweb_deploy/bin/reboot.sh

sudo /etc/init.d/tomcat stop
wait
sudo kill -9 java
/etc/init.d/mysqld stop
/sbin/reboot


# This is run as root, in a cron job, as such:
#
# 5 1 * * * sh /antweb/antweb_deploy/bin/reboot.sh

systemctl stop tomcat
wait
kill -9 java
/etc/init.d/mysql stop
/sbin/reboot


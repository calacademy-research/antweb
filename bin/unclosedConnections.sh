today=`date +%Y-%m-%d`
grep "overdue resource" /home/antweb/links/antwebInfo.log -A 6 > /data/antweb/web/log/unclosedConnections/$today

# Logging the stack trace by which the overdue resource was checked-out

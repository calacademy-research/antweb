today=`date +%Y-%m-%d`
#grep "overdue resource" /antweb/links/antwebInfo.log -A 6 > /data/antweb/web/log/unclosedConnections/$today

grep "Overdue resource check-out stack trace" ../links/antwebInfo.log -B 1 -A 5 | grep "BrowseAction\|overdue" > /data/antweb/log/unclosedConnections/$today

# Logging the stack trace by which the overdue resource was checked-out

# From antweb dir:
#  grep "Overdue resource check-out stack trace" logs/antwebInfo.log -B 1 -A 5 | grep "BrowseAction\|overdue"

# From: docker-compose exec antweb bash
# ls /data/antweb/web/log/unclosedConnections/


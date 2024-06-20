today=`date +%Y-%m-%d`
#grep "overdue resource" /antweb/links/antwebInfo.log -A 6 > /data/antweb/web/log/unclosedConnections/$today

grep "Overdue resource check-out stack trace" ../links/antwebInfo.log -B 1 -A 6 | grep "overdue\|BrowseAction\|TaxaPageAction" > /data/antweb/log/unclosedConnections/$today

# Logging the stack trace by which the overdue resource was checked-out

# From antweb dir:
#  grep "Overdue resource check-out stack trace" logs/antwebInfo.log -B 1 -A 5 | grep "BrowseAction\|overdue"

# From: docker-compose exec antweb bash
# ls /data/antweb/web/log/unclosedConnections/

# Try to correlate with docker log:
# tail ../links/webLogs/getConns.log

#sudo ln -sf /Applications/Docker.app/Contents/Resources/bin/docker-credential-ecr-login /usr/local/bin/docker-credential-ecr-login
#Password:
#mark@Marks-iMac antweb %
#mark@Marks-iMac antweb %
#mark@Marks-iMac antweb % ls -al /usr/local/bin/docker-credential-ecr-login
#lrwxr-xr-x  1 root  admin  75 Jun 19 16:08 /usr/local/bin/docker-credential-ecr-login -> /Applications/Docker.app/Contents/Resources/bin/docker-credential-ecr-login

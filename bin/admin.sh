# admin.sh was executed by the Antweb app at the end of the scheduled tasks.
# Now it is a crontab:
# 0 21 * * * /antweb/deploy/bin/admin.sh > /antweb/log/adminTask.log

sh unclosedConnections.sh

# Disabled as of 8/11/23 - moving this to system level so we can leverage mysqldump in the 
# docker database image.
#sh /antweb/deploy/bin/db-daily-dump.sh


#cp /mnt/backup/db/ant-currentDump.sql.gz /data/antweb/web/db/ant-currentDump.sql.gz
# Did have a softlink in the /mnt directory: ln -s /data/antweb/backup backup
# Now have changed scripts to write directly.

# Disk can get eaten up.
python3 /antweb/deploy/bin/clearDisk.py

#rm /var/log/mysqld.log
#rm /var/log/apache2/access.log.*
#rm /var/log/mysql/slow.log

# 11G
# rm /root/mysqld.log.local  

# sh backupImages.sh

# This script should be deployed into the /data directory of the stage server.
#
# Create a softlink as such:
#   [mjohnson@vAntWeb2 data]$ ln -s /home/mjohnson/antweb/bin/backupImages.sh backupImages.sh
#
# This script will copy all images from antweb server to this stage server, for backup purposes.  
# We do not copy all of /data or all of /data/antweb because that would break the stage server's configuration.

sudo rsync -avz mjohnson@antweb.org:/data/antweb/images antweb/

# To load the backup into an antweb distribution, from the webapps dir (or /data) invoke this command
# (changing the project snapshot directory name):

# scp -r mjohnson@antweb.org:/home/mjohnson/bak/projectData/projectData2011-12-12=13:32/* antweb/


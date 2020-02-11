#
# Execute as such:
#   cd /data/antweb/
#   sh /home/mark/antweb/db/upgrade/4.3/2011-04-29.sh
#

# Create new directories in webapp root.  
  
#  web - All generated content.
#    curator - all curator specific content
#      mark - these are dynamicall created at Account creation time
#      bfisher
#      ...

mkdir web
mkdir web/curator/
# Every login needs to be saved through the curator tools to create their directory.

mkdir web/projects/
  # second copies.  To be made primary some day
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.1.7/2011-04-11.sql
#

# To speed up the biota upload process, in particular with the delete statement.
create index access_group_idx on specimen (access_group);

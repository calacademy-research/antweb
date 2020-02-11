#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.26/2011-12-12.sql
#

alter table project modify column authorImage varchar(100);

#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.21/2015-01-31.sql
#

alter table specimen add column status varchar(30);

# This should be run after each specimen upload.  Takes about a minute.
update specimen s set s.status = (select status from taxon where taxon_name = s.taxon_name);
/
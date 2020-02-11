#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.11/2011-08-22.sql
#


#alter table specimen drop column is_fossil;

alter table taxon add column fossil tinyint(4);

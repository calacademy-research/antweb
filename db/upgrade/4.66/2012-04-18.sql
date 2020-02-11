#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.66/2012-04-18.sql
#


alter table description_edit modify column created timestamp default CURRENT_TIMESTAMP;

update description_edit set created = now() where created = "0000-00-00 00:00:00";

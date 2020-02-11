#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.21/2015-04-01.sql
#

alter table taxon add column hol_id int(11);
alter table homonym add column hol_id int(11);

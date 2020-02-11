#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.34.4/2015-07-14.sql
#


delete from taxon where taxon_name = "(formicidae)(formicidae)";

alter table taxon add column access_group int;
alter table homonym add column access_group int;

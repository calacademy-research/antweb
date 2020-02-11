#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.16/2014-09-25.sql
#

alter table homonym add column line_num int(11);
alter table taxon add column line_num int(11);
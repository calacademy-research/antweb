#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.57/2012-02-25.sql
#

alter table artist add column active tinyint(4) default 1;

alter table image add column modified TIMESTAMP DEFAULT 0 ON UPDATE CURRENT_TIMESTAMP;


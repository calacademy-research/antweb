#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.56/2012-02-22.sql
#

alter table operation_lock add column unlock_op varchar(30);

alter table artist add column created timestamp default CURRENT_TIMESTAMP;

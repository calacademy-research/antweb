#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.50/2012-02-07.sql
#

alter table specimen add datecollectedstartstr varchar(20), add datecollectedendstr varchar(20), add datedeterminedstr varchar(20);
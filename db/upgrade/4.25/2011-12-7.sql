#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.25/2011-12-07.sql
#

alter table specimen add column datecollectedstart date, add column datecollectedend date;
alter table specimen change column locxyaccuracy latlonmaxerror varchar(128);
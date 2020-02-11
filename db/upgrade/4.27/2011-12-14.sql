#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.27/2011-12-14.sql
#

alter table specimen change column province adm1 varchar(128), change column county adm2 varchar(128);

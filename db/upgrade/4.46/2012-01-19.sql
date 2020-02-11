#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.46/2012-01-19.sql
#

alter table specimen modify column method varchar(256), modify column dnaextractionnotes varchar(256), modify column microhabitat varchar(256);


#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.23.1/2011-10-22.sql
#

alter table project modify column specimenImage1 varchar(100), modify column specimenImage2 varchar(100), modify column specimenImage3 varchar(100);

alter table specimen modify column localitynotes text, modify column specimennotes text, add column collectionnotes text;
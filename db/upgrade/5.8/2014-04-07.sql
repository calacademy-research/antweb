#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.8/2014-04-07.sql
#

alter table statistics add column (valid_species_imaged int(11));

alter table login add column last_login timestamp;

alter table statistics add column exec_time varchar(30);
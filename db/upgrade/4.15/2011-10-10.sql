#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.15/2011-10-10.sql
#

alter table taxon add column created timestamp default now();

alter table specimen add column created timestamp default now();

alter table project add created timestamp default now();


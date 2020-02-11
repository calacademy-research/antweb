#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.0.6/2013-07-16.sql
#

alter table project add primary key (project_name);
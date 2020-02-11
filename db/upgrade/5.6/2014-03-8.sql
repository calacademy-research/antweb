#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.6/2014-03-08.sql
#

create index col_code_idx on specimen (collectioncode);

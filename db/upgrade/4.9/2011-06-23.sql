#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.9/2011-06-23.sql
#

# in the biota data these values can be ranges, but we store them explicity so that they are queryable.

#No.  Should be in Taxon.
#   alter table specimen add column is_fossil tinyint(4);
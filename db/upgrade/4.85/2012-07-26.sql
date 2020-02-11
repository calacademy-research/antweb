#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.85/2012-07-26.sql
#

# We don't know how this got incorrectly set to be "species".  Bolton fails to upload it.
update taxon set rank = "family" where taxon_name = "formicidae";

# amplyopone is not in the database.  Bolton successfully uploads it.  Where did it go?


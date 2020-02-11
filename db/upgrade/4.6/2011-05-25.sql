#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.6/2011-05-25.sql
#

# in the biota data these values can be ranges, but we store them explicity so that they are queryable.

alter table specimen add column elevation int, add column date_collected date;


#
# alter table specimen add column elevation int, add column date_collected date, add column is_fossil tinyint(4)
#, add column locxyaccuracy varchar(128), add microhabitat varchar(128), add datedetermined date
#  , add elevationmaxerror varchar(128), add localitynotes varchar(128)
#  , add dnaextractionnotes varchar(128), add specimennotes varchar(128)
#  , add datescollected varchar(128);

# alter table specimen add column locxyaccuracy varchar(128), add microhabitat varchar(128), add datedetermined date   , add elevationmaxerror varchar(128), add localitynotes varchar(128)   , add dnaextractionnotes varchar(128), add specimennotes varchar(128), add datescollected varchar(128);
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.18/2011-010-25.sql
#

create index county_idx ON specimen (county);



#select distinct taxon.subfamily, taxon.genus, taxon.species, taxon.taxon_name, taxon.valid, sp.county from taxon, specimen as sp  where taxon.species != '' and taxon.genus != '' and taxon.valid = 1 and  taxon.taxon_name = sp.taxon_name and  (sp.county='sacramento' or sp.county='san francisco' or sp.county='yolo') 

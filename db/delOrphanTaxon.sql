# This script will purge deleted taxons from Antweb
#
# Usage: mysql -u antweb -p ant < delOrphanTaxon.sql
#
# It would be a good idea to back up the database first...
#
# mysqldump --opt --skip-lock-tables -u antweb -p ant | gzip > /home/mjohnson/bak/db/2011-03-22.sql.gz
#
# Remaining work.  Automate this (carefully).

delete from favorite_images 
 where taxon_name not in (select distinct taxon_name from proj_taxon) 
   and taxon_name not in (select distinct taxon_name from specimen);

# These do not seem to occur   
delete from similar_species where taxon_name not in
  (select distinct taxon_name from proj_taxon) and taxon_name not in
  (select distinct taxon_name from specimen);   

delete from similar_species 
 where similar_species not in (select distinct taxon_name from proj_taxon) 
   and similar_species not in (select distinct taxon_name from specimen);

delete from synonymy where taxon_name not in
  (select distinct taxon_name from proj_taxon) and taxon_name not in
  (select distinct taxon_name from specimen);   

delete from description_edit where taxon_name
not in (select distinct taxon_name from proj_taxon) and taxon_name not
in (select distinct taxon_name from specimen);

# These do not seem to occur
delete from taxon 
 where taxon_name not in (select distinct taxon_name from proj_taxon) 
   and taxon_name not in (select distinct taxon_name from specimen);
   

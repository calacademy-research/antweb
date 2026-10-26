# This script will purge deleted taxons from Antweb
#
# Usage: mysql -u antweb -p ant < delOrphanTaxon.sql
#
# It would be a good idea to back up the database first...
#
# mysqldump --opt --skip-lock-tables -u antweb -p ant | gzip > /home/mjohnson/bak/db/2011-03-22.sql.gz
#
# Remaining work.  Automate this (carefully).

# 2026-10-23: the statements for favorite_images, similar_species and synonymy are removed: those tables were
# dropped (db/upgrade/8.115/2026-10-23-drop-old-tables-widen-group-name.sql; archived to
# /mnt/backup/archive/old_tables_20261023.sql.gz).

delete from description_edit where taxon_name
not in (select distinct taxon_name from proj_taxon) and taxon_name not
in (select distinct taxon_name from specimen);

# These do not seem to occur
delete from taxon 
 where taxon_name not in (select distinct taxon_name from proj_taxon) 
   and taxon_name not in (select distinct taxon_name from specimen);
   

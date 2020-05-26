# This script, launched weekly by cronjob, will dump data to /data/antweb/web/bak as a data protection.

today=`date +%Y-%m-%d`
mysql -u antweb -pf0rm1c6 ant -e  "select g.name, gt.* from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id order by name, taxon_name" > /data/antweb/web/bak/taxonSets/geolocale/$today
mysql -u antweb -pf0rm1c6 ant -e  "select * from proj_taxon order by project_name, taxon_name" > /data/antweb/web/bak/taxonSets/project/$today  
mysql -u antweb -pf0rm1c6 ant -e  "select * from museum_taxon order by code" > /data/antweb/web/bak/taxonSets/museum/$today  
mysql -u antweb -pf0rm1c6 ant -e  "select * from bioregion_taxon" > /data/antweb/web/bak/taxonSets/bioregion/$today  

# This script, launched weekly by cronjob, will dump data to /mnt/antweb/web/bak as a data protection.

today=$(date +%Y-%m-%d)
mysql -h mysql -u antweb -pf0rm1c6 ant -e "select g.name, gt.* from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id order by name, taxon_name" >/mnt/antweb/web/bak/taxonSets/geolocale/$today
mysql -h mysql -u antweb -pf0rm1c6 ant -e "select * from proj_taxon order by project_name, taxon_name" >/mnt/antweb/web/bak/taxonSets/project/"$today"
mysql -h mysql -u antweb -pf0rm1c6 ant -e "select * from museum_taxon order by code" >/mnt/antweb/web/bak/taxonSets/museum/"$today"
mysql -h mysql -u antweb -pf0rm1c6 ant -e "select * from bioregion_taxon" >/mnt/antweb/web/bak/taxonSets/bioregion/"$today"

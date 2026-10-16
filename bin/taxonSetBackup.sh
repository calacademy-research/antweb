# This script, launched weekly by cronjob, will dump data to /mnt/antweb/web/bak as a data protection.

# MySQL login from the server-only etc/db.properties (never in git).
dbconf=""
for f in /antweb/deploy/etc/db.properties "$(dirname "$0")/../etc/db.properties" /root/antweb/etc/db.properties; do
  [ -f "$f" ] && dbconf="$f" && break
done
[ -n "$dbconf" ] || { echo "db.properties not found" >&2; exit 1; }
MYSQL_PWD=$(sed -n 's/^password=//p' "$dbconf")
export MYSQL_PWD

today=$(date +%Y-%m-%d)
mysql -h mysql -u antweb ant -e "select g.name, gt.* from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id order by name, taxon_name" >/mnt/antweb/web/bak/taxonSets/geolocale/$today
mysql -h mysql -u antweb ant -e "select * from proj_taxon order by project_name, taxon_name" >/mnt/antweb/web/bak/taxonSets/project/"$today"
mysql -h mysql -u antweb ant -e "select * from museum_taxon order by code" >/mnt/antweb/web/bak/taxonSets/museum/"$today"
mysql -h mysql -u antweb ant -e "select * from bioregion_taxon" >/mnt/antweb/web/bak/taxonSets/bioregion/"$today"

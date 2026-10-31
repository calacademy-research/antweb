# This script, launched weekly by cronjob, will dump data to /mnt/antweb/web/bak as a data protection.
# Each file is written as <date>.tmp and moved into place only when mysql succeeded and wrote something, so an
# empty or partial file never looks like a backup (2026-10-30). The last line says ok or failed; the exit status
# is 1 when any table failed. TAXONSET_BAK_DIR overrides the folder (tests).

# MySQL login from the server-only etc/db.properties (never in git).
dbconf=""
for f in /antweb/deploy/etc/db.properties "$(dirname "$0")/../etc/db.properties" /root/antweb/etc/db.properties; do
  [ -f "$f" ] && dbconf="$f" && break
done
[ -n "$dbconf" ] || { echo "db.properties not found" >&2; echo "taxonSetBackup failed: db.properties not found"; exit 1; }
MYSQL_PWD=$(sed -n 's/^password=//p' "$dbconf")
export MYSQL_PWD

bak="${TAXONSET_BAK_DIR:-/mnt/antweb/web/bak/taxonSets}"
today=$(date +%Y-%m-%d)
failed=""

backup() {
  out="$bak/$1/$today"
  if mysql -h mysql -u antweb ant -e "$2" > "$out.tmp" && [ -s "$out.tmp" ]; then
    mv "$out.tmp" "$out" || failed="$failed $1"
  else
    rm -f "$out.tmp"
    failed="$failed $1"
  fi
}

backup geolocale "select g.name, gt.* from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id order by name, taxon_name"
backup project "select * from proj_taxon order by project_name, taxon_name"
backup museum "select * from museum_taxon order by code"
backup bioregion "select * from bioregion_taxon"

if [ -n "$failed" ]; then
  echo "taxonSetBackup $today failed:$failed"
  exit 1
fi
echo "taxonSetBackup $today ok"

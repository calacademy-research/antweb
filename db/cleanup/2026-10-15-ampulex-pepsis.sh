#!/bin/bash
# One-time cleanup for GitHub #94, run on the AntWeb host (prod) as root:
#
#   bash ~/antweb/db/cleanup/2026-10-15-ampulex-pepsis.sh           # preview: counts only, changes nothing
#   bash ~/antweb/db/cleanup/2026-10-15-ampulex-pepsis.sh --apply   # backup, then fix in one transaction
#
# Renames the broken non-ant rows created by a 2026-06-05 upload with the subfamily in
# parentheses, the same fix as the 2026-05-23 cleanup:
#   (ampulicinae)ampulex...  ->  ampulicinaeampulex...   subfamily (ampulicinae) -> ampulicinae
#   (pepsinae)pepsis...      ->  pepsinaepepsis...       subfamily (pepsinae)    -> pepsinae
#   family pepsidae -> pompilidae;  taxon.status morphotaxon -> unrecognized
# in every table that has a taxon_name column. All updates run in one transaction:
# any error leaves the database unchanged. Restart the antweb container afterwards.
set -euo pipefail

DB=ant
APPLY=0; [ "${1:-}" = "--apply" ] && APPLY=1
C=$(docker ps -qf name=mysql | head -1)
[ -n "$C" ] || { echo "MySQL container not found"; exit 1; }

read -rsp "MySQL password for the antweb login: " MYSQL_PWD; echo
export MYSQL_PWD
q()    { docker exec -i -e MYSQL_PWD "$C" mysql -u antweb -N -B "$DB" "$@"; }

OLD="(taxon_name LIKE '(ampulicinae)ampulex%' OR taxon_name LIKE '(pepsinae)pepsis%')"

TABLES=$(q -e "SELECT c.table_name FROM information_schema.columns c
  JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
  WHERE c.table_schema = '$DB' AND c.column_name = 'taxon_name' AND t.table_type = 'BASE TABLE'
  ORDER BY c.table_name")

hascol() { q -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='$1' AND column_name='$2'"; }

# The clean names must not exist yet.
CLASH=$(q -e "SELECT COUNT(*) FROM taxon WHERE taxon_name LIKE 'ampulicinaeampulex%' OR taxon_name LIKE 'pepsinaepepsis%'")
if [ "$CLASH" != "0" ]; then
  echo "STOP: $CLASH taxon rows already have the clean names; this needs a manual merge."
  q -e "SELECT taxon_name, taxarank, status, source FROM taxon WHERE taxon_name LIKE 'ampulicinaeampulex%' OR taxon_name LIKE 'pepsinaepepsis%'"
  exit 1
fi

SQL=$(mktemp)
echo "START TRANSACTION;" > "$SQL"
TOTAL=0
echo "Rows to change, per table:"
for t in $TABLES; do
  n=$(q -e "SELECT COUNT(*) FROM \`$t\` WHERE $OLD")
  [ "$n" = "0" ] && continue
  TOTAL=$((TOTAL + n))
  printf "  %-28s %s\n" "$t" "$n"
  set_list="taxon_name = REPLACE(REPLACE(taxon_name, '(ampulicinae)ampulex', 'ampulicinaeampulex'), '(pepsinae)pepsis', 'pepsinaepepsis')"
  [ "$(hascol "$t" subfamily)" = "1" ] && set_list="$set_list, subfamily = CASE subfamily WHEN '(ampulicinae)' THEN 'ampulicinae' WHEN '(pepsinae)' THEN 'pepsinae' ELSE subfamily END"
  [ "$(hascol "$t" family)" = "1" ] && set_list="$set_list, family = IF(family = 'pepsidae', 'pompilidae', family)"
  [ "$(hascol "$t" parent_taxon_name)" = "1" ] && set_list="$set_list, parent_taxon_name = REPLACE(REPLACE(parent_taxon_name, '(ampulicinae)ampulex', 'ampulicinaeampulex'), '(pepsinae)pepsis', 'pepsinaepepsis')"
  [ "$t" = "taxon" ] && set_list="$set_list, status = IF(status = 'morphotaxon', 'unrecognized', status)"
  echo "UPDATE \`$t\` SET $set_list WHERE $OLD;" >> "$SQL"
done
echo "COMMIT;" >> "$SQL"
echo "Total rows: $TOTAL"

if [ "$APPLY" = "0" ]; then
  echo
  echo "Preview only, nothing changed. Run again with --apply to back up and fix."
  rm -f "$SQL"; exit 0
fi

BK=/root/taxon-cleanup-2026-10-15
mkdir -p "$BK"
for t in $TABLES; do
  docker exec -e MYSQL_PWD "$C" mysqldump -u antweb --no-create-info --skip-triggers --single-transaction \
    "$DB" "$t" --where="$OLD" > "$BK/$t.sql"
done
cp "$SQL" "$BK/applied.sql"
echo "Backup written to $BK (restore: load the .sql files after deleting the renamed rows)."

q < "$SQL"
rm -f "$SQL"

LEFT=$(for t in $TABLES; do q -e "SELECT COUNT(*) FROM \`$t\` WHERE $OLD"; done | awk "{s+=\$1} END {print s+0}")
echo "Done. Old-style rows left: $LEFT (expect 0)."
q -e "SELECT taxon_name, taxarank, family, subfamily, status FROM taxon WHERE genus IN ('ampulex','pepsis') ORDER BY genus, taxarank DESC, taxon_name"
echo
echo "Next: docker restart antweb_antweb_1   (AntWeb reloads its taxon list; site back in 2-4 min)"

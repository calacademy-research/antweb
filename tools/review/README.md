# Read-only review queries

SELECT-only queries for reviewing where the names on AntWeb's place lists come from (design rules,
claude/antweb-list-design-rules.md in the project). They change nothing. Run on antweb-prod outside 05:00-08:00:

```
ssh root@165.232.137.86
Q=antcat-genus-rows; F=/root/antweb/etc/db.properties; O=/root/$Q-$(date +%Y%m%d).tsv
curl -sf https://raw.githubusercontent.com/calacademy-research/antweb/tools/review-queries/tools/review/$Q.sql | docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -B > $O 2> $O.err; echo "exit $? rows $(($(wc -l < $O) - 1))"; cat $O.err
```

- antcat-genus-rows.sql: genus rows from the AntCat import with no species row on the same list.
- list-sources-summary.sql: rows per source, list type and rank, with oldest and newest dates.

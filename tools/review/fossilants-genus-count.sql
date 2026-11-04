-- READ-ONLY (2026-11-03). Why the Fossil ants project counts one genus more than it lists (Genera 259 against 258,
-- four subfamily rows one over; docs/numbers.md, "Open (2026-10-27)"). Three answers, one per possible cause.

-- 0. The stored number.
SELECT project_name, genus_count FROM project WHERE project_name = 'fossilants';

-- 1. Counted genera under no counted subfamily row (cause 1, B76).
SELECT t.taxon_name, t.status, t.fossil, t.parent_taxon_name, pt.taxarank parent_rank, pt.status parent_status,
       (SELECT COUNT(*) FROM proj_taxon x WHERE x.project_name = 'fossilants' AND x.taxon_name = t.parent_taxon_name) parent_on_list
FROM proj_taxon l
JOIN taxon t ON t.taxon_name = l.taxon_name
LEFT JOIN taxon pt ON pt.taxon_name = t.parent_taxon_name
WHERE l.project_name = 'fossilants' AND t.taxarank = 'genus' AND t.status IN ('valid', 'morphotaxon')
  AND t.family = 'formicidae' AND t.taxon_name NOT LIKE '%(%' AND t.taxon_name NOT LIKE '%)%'
  AND NOT (pt.taxarank = 'subfamily' AND pt.status = 'valid'
           AND EXISTS (SELECT 1 FROM proj_taxon x WHERE x.project_name = 'fossilants' AND x.taxon_name = t.parent_taxon_name));

-- 2. Subfamily rows whose stored genus count differs from their genus rows (all counted, and valid only as listed).
SELECT l.taxon_name, l.genus_count,
       (SELECT COUNT(*) FROM proj_taxon c JOIN taxon ct ON ct.taxon_name = c.taxon_name
        WHERE c.project_name = 'fossilants' AND ct.taxarank = 'genus' AND ct.parent_taxon_name = l.taxon_name
          AND ct.status IN ('valid', 'morphotaxon') AND ct.taxon_name NOT LIKE '%(%' AND ct.taxon_name NOT LIKE '%)%') counted,
       (SELECT COUNT(*) FROM proj_taxon c JOIN taxon ct ON ct.taxon_name = c.taxon_name
        WHERE c.project_name = 'fossilants' AND ct.taxarank = 'genus' AND ct.subfamily = l.taxon_name AND ct.status = 'valid') listed_valid
FROM proj_taxon l JOIN taxon t ON t.taxon_name = l.taxon_name
WHERE l.project_name = 'fossilants' AND t.taxarank = 'subfamily'
HAVING l.genus_count <> counted OR l.genus_count <> listed_valid;

-- 3. Genus rows with no child row on the list (cause 2: the nightly set 5 deletes these after set 3 has counted them).
SELECT l.taxon_name, t.status, l.source, l.created
FROM proj_taxon l JOIN taxon t ON t.taxon_name = l.taxon_name
WHERE l.project_name = 'fossilants' AND t.taxarank = 'genus'
  AND NOT EXISTS (SELECT 1 FROM proj_taxon c JOIN taxon ct ON ct.taxon_name = c.taxon_name
                  WHERE c.project_name = 'fossilants' AND ct.parent_taxon_name = l.taxon_name);

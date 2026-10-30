package test.org.calacademy.antweb.util;

import java.util.*;

import junit.framework.TestCase;

import org.calacademy.antweb.Countable;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.WorldantsUploadDb;
import org.calacademy.antweb.util.CountInvariants;

/**
 * Decisions of Brian Fisher on 2026-10-30, without a database (the database behaviour: GeneraRuleDbTest, MySQL 8.4 in CI):
 *
 *   Genera   valid genera and morphogenera only (PlaceTotals.countsAsGenus). A genus value with parentheses and a
 *            subfamily or family name ("(myrmicinae)", "(formicidae)", "(dacetini)") is undetermined, never a genus; a
 *            genus name AntCat does not know (status unrecognized: "(formicidae)polyrachis", a misspelling, an old name)
 *            is not a genus either (it stays in AntWeb with its specimens); a stray parenthesis ("Amblyoponinae)") neither.
 *            The same rule on every kind of place, the subfamily rows' "N Genera" of every list, upload groups and the
 *            count check. Subfamilies: no placeholder rows ("(formicidae)", status morphotaxon since 2017).
 *   Worldants alert   "No Worldants changes in last week" counts the imports whose file size differs from the previous
 *            import's (WorldantsUploadDb.CHANGE_COUNT_SQL), not from its own backup copy (always the same size).
 */
public class CountRules20261030Test extends TestCase {

    public void testGeneraAreValidGeneraAndMorphogenera() {
        assertTrue(PlaceTotals.isCountedGenus("valid", "myrmicinaepheidole", "pheidole"));
        assertTrue("a morphogenus", PlaceTotals.isCountedGenus("morphotaxon", "formicinaeform_gen-afrc-za01", "form_gen-afrc-za01"));
        assertFalse("identified only to subfamily", PlaceTotals.isCountedGenus("indetermined", "myrmicinae(myrmicinae)", "(myrmicinae)"));
        assertFalse("a placeholder is never a genus, whatever its status",
            PlaceTotals.isCountedGenus("morphotaxon", "(formicidae)(formicidae)", "(formicidae)"));
        assertFalse("a genus name AntCat does not know", PlaceTotals.isCountedGenus("unrecognized", "(formicidae)polyrachis", "polyrachis"));
        assertFalse("unrecognized, even without parentheses", PlaceTotals.isCountedGenus("unrecognized", "formicinaepolyrachis", "polyrachis"));
        assertFalse("under a placeholder subfamily", PlaceTotals.isCountedGenus("valid", "(formicidae)pyramica", "pyramica"));
        assertFalse("a stray parenthesis", PlaceTotals.isCountedGenus("morphotaxon", "amblyoponinae)", "amblyoponinae)"));
        assertFalse("a tribe in parentheses", PlaceTotals.isCountedGenus("unrecognized", "(dacetini)", "(dacetini)"));
        for (String s : new String[] {"indetermined", "unidentifiable", "unrecognized", null}) {
            assertFalse(s, PlaceTotals.isCountedGenus(s, "myrmicinaepheidole", "pheidole"));
        }
        assertEquals("('valid', 'morphotaxon')", PlaceTotals.GENUS_STATUSES);
        assertTrue(PlaceTotals.isPlaceholder("(formicidae)"));
        assertTrue(PlaceTotals.isPlaceholder("amblyoponinae)"));
        assertFalse(PlaceTotals.isPlaceholder("myrmicinaepheidole"));
    }

    public void testPlaceGeneraAndSubfamiliesTakeTheRule() {
        String g = PlaceTotals.rankCondition(PlaceTotals.GENUS);
        assertTrue(g, g.contains("t.taxarank = 'genus'"));
        assertTrue(g, g.contains("t.status in " + PlaceTotals.GENUS_STATUSES));
        assertTrue(g, g.contains(PlaceTotals.identifiedToGenus("t")));
        assertTrue(g, g.contains(PlaceTotals.notPlaceholder("t")));
        assertTrue("ants only: " + g, g.endsWith(PlaceTotals.ANTS));
        String s = PlaceTotals.rankCondition(PlaceTotals.SUBFAMILY);
        assertTrue(s, s.contains("t.status in " + PlaceTotals.COUNTABLE));
        assertTrue(s, s.contains(PlaceTotals.notPlaceholder("t")));
        String p = PlaceTotals.listedCondition(PlaceTotals.PROJECT, PlaceTotals.SUBFAMILY);
        assertTrue("a curated project's Subfamilies: every status (B67) but no placeholder: " + p, p.contains(PlaceTotals.notPlaceholder("t")));
        // every kind of place: the nightly totals (one place, all places) and the count check use the same condition
        for (String type : PlaceTotals.TYPES) {
            assertTrue(type, PlaceTotals.listedCountSql(type, PlaceTotals.GENUS).contains(PlaceTotals.countsAsGenus("t")));
            assertTrue(type, PlaceTotals.listedCountsByPlaceSql(type, PlaceTotals.GENUS).contains(PlaceTotals.countsAsGenus("t")));
            assertTrue(type, PlaceTotals.listedCountSql(type, PlaceTotals.SUBFAMILY).contains(PlaceTotals.notPlaceholder("t")));
        }
    }

    public void testSubfamilyRowsGeneraOfEveryListTakeTheRule() {
        Country c = new Country();
        c.setId(7);
        c.setName("Madagascar");
        Bioregion b = new Bioregion();
        b.setName("Afrotropical");
        Museum m = new Museum();
        m.setCode("CASC");
        Countable[] lists = {c, b, m, new Project("madants"), new Project("worldants")};
        for (Countable list : lists) {
            String genus = list.getCountChildrenQuery("genus");
            assertTrue(list + ": " + genus, genus.contains(PlaceTotals.countsAsGenus("taxon")));
            String subfamily = list.getCountChildrenQuery("subfamily");
            assertTrue(list + ": " + subfamily, subfamily.contains(PlaceTotals.notPlaceholder("taxon")));
            assertFalse(list + ": species rows keep their rule", list.getCountChildrenQuery("species").contains(PlaceTotals.countsAsGenus("taxon")));
        }
        assertEquals("", PlaceTotals.childCountCondition(PlaceTotals.SPECIES, "taxon"));
    }

    public void testUploadGroupGeneraTakeTheRule() {
        assertTrue(GroupCountDb.countColumns().contains(GroupCountDb.COUNTED_GENERA));
        assertTrue(GroupCountDb.COUNTED_GENERA.contains(PlaceTotals.countsAsGenus("g")));
        boolean found = false;
        for (String sql : GroupCountDb.totalsSqls("1, 2")) {
            if (sql.contains("s.genus")) {
                assertTrue(sql, sql.contains(GroupCountDb.COUNTED_GENERA));
                found = true;
            }
        }
        assertTrue(found);
    }

    public void testNumbersArePendingUntilANightlyRunCountsByTheGeneraRule() {
        List<String> before = Arrays.asList("  Set 4 done (" + CountInvariants.RULES_LOG + "; " + CountInvariants.COUNTING_RULES_LOG + ")");
        assertFalse(CountInvariants.rulesCounted(before));
        List<String> after = Arrays.asList("  Set 4 done (" + CountInvariants.RULES_LOG + "; x; " + CountInvariants.COUNTING_RULES_LOG
            + "; " + CountInvariants.GENERA_RULE_LOG + ")");
        assertTrue(CountInvariants.rulesCounted(after));
        assertEquals("genera rule of 2026-10-30", CountInvariants.GENERA_RULE_LOG);
    }

    public void testWorldantsChangesCompareWithThePreviousImport() {
        String sql = WorldantsUploadDb.CHANGE_COUNT_SQL;
        assertFalse("the backup copy has the size of the same file: " + sql, sql.contains("backup_file_size"));
        assertTrue(sql, sql.contains("lag("));
        assertTrue(sql, sql.contains("over (order by created, id)"));
        assertTrue("no previous import: no change", sql.contains("prev_size is not null"));
        assertTrue(sql, sql.contains("interval 7 day"));
    }
}

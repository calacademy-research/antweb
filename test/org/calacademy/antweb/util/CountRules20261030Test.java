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
 *            count check.
 *   Subfamilies   valid subfamilies only (PlaceTotals.countsAsSubfamily), the same principle: the placeholder
 *            "(formicidae)" (status morphotaxon since 2017) and misspelled subfamily names (status unrecognized) are not
 *            subfamilies; every kind of place, curated projects too (this replaced B67 of 2026-10-27), the Formicidae
 *            row's "N Subfamilies" of every list and the count check. Upload groups already counted valid subfamilies.
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
        assertTrue(s, s.contains("t.taxarank = 'subfamily'"));
        assertTrue(s, s.contains(PlaceTotals.countsAsSubfamily("t")));
        assertFalse("valid subfamilies only, not the counted statuses: " + s, s.contains(PlaceTotals.COUNTABLE));
        assertTrue("ants only: " + s, s.endsWith(PlaceTotals.ANTS));
        String p = PlaceTotals.listedCondition(PlaceTotals.PROJECT, PlaceTotals.SUBFAMILY);
        assertEquals("a curated project's Subfamilies: the same rule (B67 superseded)", s, p);
        // every kind of place: the nightly totals (one place, all places) and the count check use the same condition
        for (String type : PlaceTotals.TYPES) {
            assertTrue(type, PlaceTotals.listedCountSql(type, PlaceTotals.GENUS).contains(PlaceTotals.countsAsGenus("t")));
            assertTrue(type, PlaceTotals.listedCountsByPlaceSql(type, PlaceTotals.GENUS).contains(PlaceTotals.countsAsGenus("t")));
            assertTrue(type, PlaceTotals.listedCountSql(type, PlaceTotals.SUBFAMILY).contains(PlaceTotals.countsAsSubfamily("t")));
            assertTrue(type, PlaceTotals.listedCountsByPlaceSql(type, PlaceTotals.SUBFAMILY).contains(PlaceTotals.countsAsSubfamily("t")));
        }
    }

    public void testSubfamiliesAreValidSubfamilies() {
        assertTrue(PlaceTotals.isCountedSubfamily("valid", "myrmicinae"));
        assertFalse("the placeholder", PlaceTotals.isCountedSubfamily("morphotaxon", "(formicidae)"));
        assertFalse("a placeholder is never a subfamily, whatever its status", PlaceTotals.isCountedSubfamily("valid", "(formicidae)"));
        assertFalse("a misspelled subfamily name", PlaceTotals.isCountedSubfamily("unrecognized", "myrmicicinae"));
        for (String s : new String[] {"morphotaxon", "indetermined", "unidentifiable", "unrecognized", "synonym", null}) {
            assertFalse(s, PlaceTotals.isCountedSubfamily(s, "myrmicinae"));
        }
        String sql = PlaceTotals.countsAsSubfamily("t");
        assertTrue(sql, sql.contains("t.status = 'valid'"));
        assertTrue(sql, sql.contains(PlaceTotals.notPlaceholder("t")));
        assertEquals(" and " + PlaceTotals.countsAsSubfamily("a"), PlaceTotals.childCountCondition(PlaceTotals.SUBFAMILY, "a"));
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
            assertTrue(list + ": " + subfamily, subfamily.contains(PlaceTotals.countsAsSubfamily("taxon")));
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
        assertEquals("genera and subfamilies rule of 2026-10-30", CountInvariants.GENERA_RULE_LOG);
        // a run that wrote the genera rule alone (before the subfamilies rule) is still pending
        List<String> generaOnly = Arrays.asList("  Set 4 done (" + CountInvariants.RULES_LOG + "; x; " + CountInvariants.COUNTING_RULES_LOG
            + "; genera rule of 2026-10-30)");
        assertFalse(CountInvariants.rulesCounted(generaOnly));
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

package test.org.calacademy.antweb;

import java.util.ArrayList;

import junit.framework.TestCase;

import org.calacademy.antweb.Taxon;

/**
 * The image of a genus on a place's genus images page is chosen from the names of that genus in the place
 * (Taxon.getUnpickedDefault). Found 2026-10-29 by Brian Fisher: Crematogaster and Pheidole had no image on the
 * Afrotropical page because their name list was over 10,000 characters and was dropped. The names are now checked
 * in groups (Taxon.setStrChunks). No database. Runs in CI (.github/workflows/tests.yaml).
 */
public class TaxonImageNamesTest extends TestCase {

    private static ArrayList<String> names(int n) {
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(String.format("myrmicinaepheidole afr%04d", i));
        return list;
    }

    public void testALargeGenusIsNotDropped() {
        ArrayList<String> pheidole = names(700);   // over 10,000 characters as one list
        assertTrue(String.join("','", pheidole).length() > 10000);
        ArrayList<String> chunks = Taxon.setStrChunks(pheidole);
        assertEquals(3, chunks.size());
        for (String chunk : chunks) assertTrue(chunk.length() < 10000);
    }

    public void testEveryNameOnceInOrder() {
        ArrayList<String> list = names(600);
        StringBuilder all = new StringBuilder();
        for (String chunk : Taxon.setStrChunks(list)) all.append(chunk);
        String joined = all.toString();
        int last = -1;
        for (String name : list) {
            int at = joined.indexOf("'" + name + "'");
            assertTrue(name, at > last);
            assertEquals(name, at, joined.lastIndexOf("'" + name + "'"));
            last = at;
        }
    }

    public void testGroupSizeAndCap() {
        assertEquals(1, Taxon.setStrChunks(names(1)).size());
        assertEquals(1, Taxon.setStrChunks(names(Taxon.NAMES_PER_QUERY)).size());
        assertEquals(2, Taxon.setStrChunks(names(Taxon.NAMES_PER_QUERY + 1)).size());
        assertEquals(Taxon.MAX_NAMES / Taxon.NAMES_PER_QUERY, Taxon.setStrChunks(names(22000)).size());
    }

    public void testNoNames() {
        assertTrue(Taxon.setStrChunks(new ArrayList<>()).isEmpty());
        assertTrue(Taxon.setStrChunks(null).isEmpty());
    }

    public void testQuotesEscaped() {
        ArrayList<String> list = new ArrayList<>();
        list.add("myrmicinaepheidole o'brieni");
        assertFalse(Taxon.setStrChunks(list).get(0).contains("o'brieni"));
    }
}

package test.org.calacademy.antweb;

import junit.framework.TestCase;

import org.calacademy.antweb.*;

/**
 * BrowseAction.isRank: the page-level blocks of browse.do and images.do (genus rows, subfamily rows, Not curated,
 * place specimens, Fossil button) run for their own rank only. Species extends Genus extends Subfamily extends Family,
 * so instanceof matched every lower rank (2026-11-17: species images pages read the genus rows of their whole
 * subfamily). No database.
 */
public class BrowseActionRankTest extends TestCase {

    private static Taxon of(Taxon t, String rank) {
        t.setRank(rank);
        return t;
    }

    public void testASpeciesIsNotASubfamilyOrFamilyOrGenusPage() {
        Taxon species = of(new Species(), Rank.SPECIES);
        assertTrue(species instanceof Subfamily);   // why instanceof was wrong
        assertFalse(BrowseAction.isRank(species, Rank.SUBFAMILY));
        assertFalse(BrowseAction.isRank(species, Rank.FAMILY));
        assertFalse(BrowseAction.isRank(species, Rank.GENUS));
        assertTrue(BrowseAction.isRank(species, Rank.SPECIES));
    }

    public void testAGenusIsOnlyAGenusPage() {
        Taxon genus = of(new Genus(), Rank.GENUS);
        assertTrue(BrowseAction.isRank(genus, Rank.GENUS));
        assertFalse(BrowseAction.isRank(genus, Rank.SUBFAMILY));
        assertFalse(BrowseAction.isRank(genus, Rank.FAMILY));
    }

    public void testSubfamilyAndFamily() {
        assertTrue(BrowseAction.isRank(of(new Subfamily(), Rank.SUBFAMILY), Rank.SUBFAMILY));
        assertFalse(BrowseAction.isRank(of(new Subfamily(), Rank.SUBFAMILY), Rank.FAMILY));
        assertTrue(BrowseAction.isRank(of(new Family(), Rank.FAMILY), Rank.FAMILY));
    }

    public void testNullsAreNoRank() {
        assertFalse(BrowseAction.isRank(null, Rank.GENUS));
        assertFalse(BrowseAction.isRank(of(new Genus(), null), Rank.GENUS));
        assertFalse(BrowseAction.isRank(of(new Genus(), Rank.GENUS), null));
    }
}

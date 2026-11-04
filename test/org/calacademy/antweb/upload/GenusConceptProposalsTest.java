package test.org.calacademy.antweb.upload;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.curate.GenusConceptRulesAction;
import org.calacademy.antweb.upload.GenusConceptProposals;
import org.calacademy.antweb.upload.GenusConceptProposals.Kind;
import org.calacademy.antweb.upload.GenusConceptProposals.Moved;
import org.calacademy.antweb.upload.GenusConceptProposals.Pair;

/**
 * Old genus concepts proposed from AntCat's data (Brian Fisher, 2026-11-03), without a database: the classification
 * of each (old genus, bioregion) as PROPOSE, SPLIT, MIXED or no valid new genus, what is not listed, the order, the
 * evidence and the page. The queries are tested in SpecimenNameUpdateDbTest.
 */
public class GenusConceptProposalsTest extends TestCase {

    static Moved moved(String newGenus, int species, boolean valid, String... examples) {
        Moved m = new Moved(newGenus);
        m.species = species;
        m.newGenusStatus = valid ? Status.VALID : Status.SYNONYM;
        m.newGenusRank = "genus";
        m.examples.addAll(List.of(examples));
        return m;
    }

    static Pair pair(String genus, String bioregion, int specimens, Moved... moves) {
        Pair p = new Pair(genus, bioregion);
        p.genusStatus = Status.VALID;
        p.specimensByGroup.put(28, specimens);
        for (Moved m : moves) p.moved.put(m.newGenus, m);
        return p;
    }

    public void testProposeWhenEverySpeciesWentToOneGenus() {
        Pair p = pair("ponerinaemesoponera", "Afrotropical", 319,
            moved("ponerinaexiphopelta", 3, true, "ponerinaemesoponera ambigua>ponerinaexiphopelta ambigua"));
        assertEquals(Kind.PROPOSE, GenusConceptProposals.classify(p, Set.of()));
        assertEquals("ponerinaexiphopelta", p.newGenus());
        assertEquals("3 of 3 species of Mesoponera in Afrotropical moved to Xiphopelta", GenusConceptProposals.evidence(p));
        assertEquals(List.of("Mesoponera ambigua -> Xiphopelta ambigua"), GenusConceptProposals.examples(p, 5));
    }

    public void testManualCases() {
        Pair split = pair("ponerinaealpha", "Malagasy", 5, moved("ponerinaebeta", 2, true), moved("ponerinaegamma", 1, true));
        assertEquals(Kind.SPLIT, GenusConceptProposals.classify(split, Set.of()));
        assertNull(split.newGenus());
        assertEquals("3 species of Alpha in Malagasy moved to Beta (2), Gamma (1)", GenusConceptProposals.evidence(split));

        Pair mixed = pair("ponerinaealpha", "Neotropical", 5, moved("ponerinaebeta", 1, true));
        mixed.validSpecies = 2;
        assertEquals(Kind.MIXED, GenusConceptProposals.classify(mixed, Set.of()));
        assertEquals("1 of 1 species of Alpha in Neotropical moved to Beta; 2 valid species of Alpha still in Neotropical",
            GenusConceptProposals.evidence(mixed));

        Pair noTarget = pair("ponerinaealpha", "Oceania", 5, moved("ponerinaebeta", 1, false));
        assertEquals("one genus, but not a valid genus row", Kind.NO_VALID_TARGET, GenusConceptProposals.classify(noTarget, Set.of()));
    }

    public void testNotListed() {
        Pair synonym = pair("ponerinaesyn", "Afrotropical", 5, moved("ponerinaebeta", 1, true));
        synonym.genusStatus = Status.SYNONYM;
        assertEquals("the genus synonym rule does it", Kind.SYNONYM_GENUS, GenusConceptProposals.classify(synonym, Set.of()));
        Pair ruled = pair("ponerinaemesoponera", "Malagasy", 1, moved("ponerinaexiphopelta", 1, true));
        assertEquals(Kind.RULED, GenusConceptProposals.classify(ruled, Set.of(GenusConceptProposals.key("ponerinaemesoponera", "Malagasy"))));
        Pair none = pair("ponerinaealpha", "Australasia", 5);
        assertEquals("no moved species known from the bioregion", Kind.NONE, GenusConceptProposals.classify(none, Set.of()));
        none.validSpecies = 4;
        assertEquals(Kind.NONE, GenusConceptProposals.classify(none, Set.of()));
    }

    public void testListedOrderAndPage() {
        Pair small = pair("ponerinaesmall", "Afrotropical", 2, moved("ponerinaebeta", 1, true));
        Pair big = pair("ponerinaebig", "Afrotropical", 300, moved("ponerinaebeta", 4, true, "ponerinaebig a>ponerinaebeta a"));
        Pair split = pair("ponerinaealpha", "Malagasy", 900, moved("ponerinaebeta", 2, true), moved("ponerinaegamma", 1, true));
        Pair hidden = pair("ponerinaealpha", "Australasia", 5);
        List<Pair> listed = GenusConceptProposals.listed(List.of(small, split, hidden, big), Set.of());
        assertEquals("PROPOSE first, most specimens first; NONE left out", List.of(big, small, split), listed);

        String html = GenusConceptRulesAction.proposalsHtml(listed, "<input type=\"hidden\" name=\"_csrf\" value=\"t\">", "");
        assertTrue(html, html.contains("<h2>Proposed (2)</h2>"));
        assertTrue(html, html.contains("name=\"op\" value=\"approve\""));
        assertTrue(html, html.contains("name=\"newGenus\" value=\"ponerinaebeta\""));
        assertTrue(html, html.contains("name=\"_csrf\""));
        assertTrue(html, html.contains("Split: the species went to two or more genera (manual) (1)"));
        assertEquals("approve and reject per proposal only", 4, html.split("type=\"submit\"", -1).length - 1);
        assertTrue(GenusConceptRulesAction.rulesHtml(List.of()).contains("None yet."));
    }
}

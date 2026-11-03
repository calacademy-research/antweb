package test.org.calacademy.antweb.upload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.upload.CurrentValidNameRule;
import org.calacademy.antweb.upload.GenusConceptRule;
import org.calacademy.antweb.upload.UploadNameReasons;

/**
 * Old genus concepts (Brian Fisher, 2026-11-03; GenusConceptRule), without a database: rule 1, Mesoponera in the
 * Afrotropical and Malagasy regions is Xiphopelta. Morphospecies and indets of those bioregions only; species names
 * never; the new genus must be a valid genus row. The database half is in SpecimenNameUpdateDbTest.
 */
public class GenusConceptRuleTest extends TestCase {

    static final Map<String, String[]> TAXA = new HashMap<>();   // status, taxarank
    static {
        TAXA.put("ponerinaemesoponera", new String[] {Status.VALID, "genus"});
        TAXA.put("ponerinaexiphopelta", new String[] {Status.VALID, "genus"});
        TAXA.put("ponerinaemesoponera ambigua", new String[] {Status.VALID, "species"});
        TAXA.put("ponerinaeoldgenus", new String[] {Status.VALID, "genus"});
        TAXA.put("ponerinaesyngenus", new String[] {Status.SYNONYM, "genus"});
    }

    static final CurrentValidNameRule.Lookup LOOKUP = new CurrentValidNameRule.Lookup() {
        public CurrentValidNameRule.TaxonRow getTaxon(String name) {
            String[] t = TAXA.get(name);
            return t == null ? null : new CurrentValidNameRule.TaxonRow(t[0], null, t[1]);
        }
        public String getCurrentValidTaxonName(String name) { return null; }
        public boolean isHomonym(String name) { return false; }
    };

    static String move(String name, String bioregion) throws Exception {
        GenusConceptRule.Move m = GenusConceptRule.decide(GenusConceptRule.SEED, name, bioregion, LOOKUP);
        return m == null ? null : m.newName;
    }

    /** The seed of table genus_concept_rule: rule 1, approved by Brian Fisher, 2026-11-03. */
    public void testRuleOneIsTheSeed() {
        assertEquals(1, GenusConceptRule.SEED.size());
        GenusConceptRule.Rule r = GenusConceptRule.SEED.get(0);
        assertEquals("ponerinaemesoponera", r.oldGenus);
        assertEquals("ponerinaexiphopelta", r.newGenus);
        assertEquals(Set.of("Afrotropical", "Malagasy"), r.bioregions);
        assertEquals("Brian Fisher", r.approvedBy);
        assertEquals("2026-11-03", r.approvedOn);
    }

    public void testIndetsAndMorphospeciesOfTheBioregionsMove() throws Exception {
        assertEquals("ponerinaexiphopelta (indet)", move("ponerinaemesoponera (indet)", "Afrotropical"));
        assertEquals("MRACFOR001128", "ponerinaexiphopelta (indet)", move("ponerinaemesoponera (indet)", "Malagasy"));
        assertEquals("the code part kept", "ponerinaexiphopelta kg01", move("ponerinaemesoponera kg01", "Afrotropical"));
        assertEquals("ponerinaexiphopelta sp-a", move("ponerinaemesoponera sp-a", "Malagasy"));
    }

    public void testWhatStays() throws Exception {
        assertNull("another bioregion: Mesoponera stays valid there", move("ponerinaemesoponera (indet)", "Indomalaya"));
        assertNull("no bioregion", move("ponerinaemesoponera (indet)", null));
        assertNull("a species name: AntCat places those", move("ponerinaemesoponera ambigua", "Afrotropical"));
        assertNull("another genus", move("ponerinaeoldgenus (indet)", "Afrotropical"));
        assertNull("the genus alone", move("ponerinaemesoponera", "Afrotropical"));
        assertNull(move(null, "Afrotropical"));
        assertNull("already moved", move("ponerinaexiphopelta (indet)", "Afrotropical"));
    }

    public void testTheNewGenusMustBeAValidGenusRow() throws Exception {
        List<GenusConceptRule.Rule> rules = List.of(
            new GenusConceptRule.Rule("ponerinaeoldgenus", Set.of("Neotropical"), "ponerinaesyngenus", "x", "2026-11-03", "test"),
            new GenusConceptRule.Rule("ponerinaemesoponera", Set.of("Neotropical"), "ponerinaenogenus", "x", "2026-11-03", "test"),
            new GenusConceptRule.Rule("ponerinaexiphopelta", Set.of("Neotropical"), "ponerinaemesoponera ambigua", "x", "2026-11-03", "test"));
        assertNull("a synonym genus", GenusConceptRule.decide(rules, "ponerinaeoldgenus (indet)", "Neotropical", LOOKUP));
        assertNull("no row", GenusConceptRule.decide(rules, "ponerinaemesoponera (indet)", "Neotropical", LOOKUP));
        assertNull("not a genus", GenusConceptRule.decide(rules, "ponerinaexiphopelta (indet)", "Neotropical", LOOKUP));
        assertEquals(Set.of("ponerinaemesoponera", "ponerinaeoldgenus", "ponerinaexiphopelta"), GenusConceptRule.oldGenera(rules));
    }

    public void testReason() throws Exception {
        GenusConceptRule.Rule r = GenusConceptRule.SEED.get(0);
        assertEquals("Old genus concept: Mesoponera in the Afrotropical region is Xiphopelta (rule approved by Brian Fisher, 2026-11-03)",
            GenusConceptRule.reason(r, "Afrotropical", null).text);
        assertEquals(Integer.valueOf(7), GenusConceptRule.reason(r, "Malagasy", 7).antcatId);
        String html = UploadNameReasons.reasonPart(GenusConceptRule.reason(r, "Malagasy", null).text, null,
            "ponerinaemesoponera (indet)", "ponerinaemesoponera (indet)", "ponerinaexiphopelta (indet)");
        assertTrue(html, html.contains("<i>Mesoponera</i> in the Malagasy region is <i>Xiphopelta</i>"));
    }
}

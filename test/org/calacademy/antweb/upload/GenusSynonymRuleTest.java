package test.org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.upload.AntcatHistory;
import org.calacademy.antweb.upload.CurrentValidNameRule;
import org.calacademy.antweb.upload.GenusSynonymRule;
import org.calacademy.antweb.upload.GenusSynonymRule.Kind;
import org.calacademy.antweb.upload.NameUpdateReason;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdate.Outcome;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.upload.UploadNameReasons;

/**
 * Brian Fisher, 2026-11-03: specimens filed under a synonym genus or without a subfamily (GenusSynonymRule),
 * without a database. A fake taxon table (name -> status, current valid name, rank) and fake genus rows by genus
 * name. The database half is in SpecimenNameUpdateDbTest.
 */
public class GenusSynonymRuleTest extends TestCase {

    static final Map<String, String[]> TAXA = new HashMap<>();   // status, current_valid_name, taxarank
    static void t(String name, String status, String cvn, String rank) { TAXA.put(name, new String[] {status, cvn, rank}); }
    static {
        t("dorylinaephyracaces", Status.SYNONYM, "dorylinaelioponera", "genus");
        t("dorylinaelioponera", Status.VALID, null, "genus");
        t("myrmicinaewasmannia", Status.SYNONYM, "Allomerus", "genus");        // AntCat's own spelling: lowercased by the lookup
        t("myrmicinaeallomerus", Status.VALID, null, "genus");
        t("myrmicinaewasmannia auropunctata", Status.OBSOLETE_COMBINATION, "myrmicinaeallomerus auropunctatus", "species");
        t("myrmicinaeallomerus auropunctatus", Status.VALID, null, "species");
        t("myrmicinaepyramica", Status.SYNONYM, "myrmicinaestrumigenys", "genus");
        t("myrmicinaestrumigenys", Status.VALID, null, "genus");
        t("myrmicinaepyramica ludovici", Status.OBSOLETE_COMBINATION, "myrmicinaestrumigenys ludovici", "species");
        t("myrmicinaestrumigenys ludovici", Status.VALID, null, "species");
        t("myrmicinaestrumigenys emmae", Status.VALID, null, "species");
        t("myrmicinaepyramica tohom", Status.OBSOLETE_COMBINATION, "myrmicinaestrumigenys hom", "species");
        t("formicinaecamponotus", Status.VALID, null, "genus");
        t("myrmicinaelost", Status.SYNONYM, "myrmicinaenowhere", "genus");          // valid genus has no row
        t("myrmicinaechain", Status.SYNONYM, "myrmicinaepyramica", "genus");        // valid genus is itself a synonym
        t("myrmicinaetospecies", Status.SYNONYM, "myrmicinaestrumigenys emmae", "genus");  // points at a species
        t("myrmicinaetohomonym", Status.SYNONYM, "myrmicinaehomgenus", "genus");
        t("myrmicinaehomgenus", Status.VALID, null, "genus");
        t("ponerinaeunavgenus", Status.UNAVAILABLE, null, "genus");
        t("ponerinaetwice", Status.VALID, null, "genus");
        t("amblyoponinaetwice", Status.VALID, null, "genus");
    }

    static final CurrentValidNameRule.Lookup LOOKUP = new CurrentValidNameRule.Lookup() {
        public CurrentValidNameRule.TaxonRow getTaxon(String name) {
            String[] r = TAXA.get(name);
            return r == null ? null : new CurrentValidNameRule.TaxonRow(r[0], r[1], r[2]);
        }
        public String getCurrentValidTaxonName(String name) {
            return TAXA.containsKey(name.toLowerCase()) ? name.toLowerCase() : null;
        }
        public boolean isHomonym(String name) {
            return "myrmicinaehomgenus".equals(name) || "myrmicinaestrumigenys hom".equals(name);
        }
    };

    /** Genus rows by genus name, real subfamilies only. */
    static final GenusSynonymRule.Genera GENERA = genus -> {
        List<String> rows = new ArrayList<>();
        for (Map.Entry<String, String[]> e : TAXA.entrySet()) {
            String n = e.getKey();
            if ("genus".equals(e.getValue()[2]) && n.endsWith("inae" + genus)) rows.add(n);
        }
        return rows;
    };

    static GenusSynonymRule.Result decide(String name) throws Exception {
        return GenusSynonymRule.decide(name, LOOKUP, GENERA);
    }

    // ------------------------------------------------------------ 1. synonym genus

    public void testIndetGoesToTheValidGenus() throws Exception {
        GenusSynonymRule.Result r = decide("dorylinaephyracaces (indet)");
        assertEquals(Kind.GENUS_MOVE, r.kind);
        assertEquals("FMNHINS0000118007", "dorylinaelioponera (indet)", r.newName);
        assertEquals("dorylinaephyracaces", r.genus);
        assertEquals("dorylinaelioponera", r.validGenus);
        assertTrue(r.moves());
        assertFalse(r.placeholder);
    }

    public void testMorphospeciesKeepsItsCode() throws Exception {
        GenusSynonymRule.Result r = decide("myrmicinaewasmannia alw02");
        assertEquals(Kind.GENUS_MOVE, r.kind);
        assertEquals("the current valid name as AntCat writes it", "myrmicinaeallomerus alw02", r.newName);
        assertEquals("myrmicinaeallomerus mg01 x", decide("myrmicinaewasmannia mg01 x").newName);
    }

    public void testValidGenusIsLeftAlone() throws Exception {
        assertEquals(Kind.NONE, decide("formicinaecamponotus mg01").kind);
        assertEquals(Kind.NONE, decide("dorylinaelioponera (indet)").kind);
        assertFalse(decide("formicinaecamponotus mg01").moves());
    }

    public void testNoUsableValidGenus() throws Exception {
        assertEquals("valid genus has no row", Kind.NO_VALID_GENUS, decide("myrmicinaelost (indet)").kind);
        assertEquals("valid genus is a synonym", Kind.NO_VALID_GENUS, decide("myrmicinaechain ab01").kind);
        assertEquals("not a genus", Kind.NO_VALID_GENUS, decide("myrmicinaetospecies ab01").kind);
        assertEquals("a homonym", Kind.NO_VALID_GENUS, decide("myrmicinaetohomonym ab01").kind);
        assertNull(decide("myrmicinaelost (indet)").newName);
    }

    public void testSpeciesNamesInASynonymGenus() throws Exception {
        assertEquals("AntCat knows it: the species rule's (first pass)", Kind.NONE, decide("myrmicinaewasmannia auropunctata").kind);
        GenusSynonymRule.Result r = decide("myrmicinaewasmannia zzunknown");
        assertEquals("not made up in another genus: listed for the AntCat editors", Kind.COMBINATION_NOT_IN_ANTCAT, r.kind);
        assertNull(r.newName);
        assertFalse(r.moves());
        assertEquals("the senior genus, for the list", "myrmicinaeallomerus", r.validGenus);
        assertEquals("no senior genus row: listed without it", Kind.COMBINATION_NOT_IN_ANTCAT, decide("myrmicinaelost zzunknown").kind);
        assertNull(decide("myrmicinaelost zzunknown").validGenus);
    }

    /**
     * The coordinator's check on production (2026-11-03): AntCat has no record of most past combinations
     * (Pyramica emmae is only "Combination in Pyramica" in Strumigenys emmae's history). The epithet is never
     * moved into the senior genus, even when the senior genus has a species of that epithet.
     */
    public void testCombinationNotInAntcatIsNeverGuessed() throws Exception {
        assertNotNull("the senior genus has the epithet", TAXA.get("myrmicinaestrumigenys emmae"));
        GenusSynonymRule.Result r = decide("(formicidae)pyramica emmae");
        assertEquals(Kind.COMBINATION_NOT_IN_ANTCAT, r.kind);
        assertNull(r.newName);
        assertEquals("myrmicinaepyramica emmae", r.rebuiltName);
        assertEquals("myrmicinaestrumigenys", r.validGenus);
        assertEquals(Kind.COMBINATION_NOT_IN_ANTCAT, decide("myrmicinaepyramica emmae").kind);
        // Once AntCat has the combination (a taxon row), the species rule moves it.
        t("myrmicinaepyramica emmae", Status.OBSOLETE_COMBINATION, "myrmicinaestrumigenys emmae", "species");
        try {
            GenusSynonymRule.Result now = decide("(formicidae)pyramica emmae");
            assertEquals(Kind.SPECIES_RULE, now.kind);
            assertEquals("myrmicinaestrumigenys emmae", now.newName);
        } finally {
            TAXA.remove("myrmicinaepyramica emmae");
        }
        // The upload: only morphospecies, indets and lines without species move with a synonym genus.
        assertFalse(SpecimenUploadParse.movesWithSynonymGenus("emmae", null));
        assertFalse(SpecimenUploadParse.movesWithSynonymGenus("emmae", "minor"));
        assertTrue(SpecimenUploadParse.movesWithSynonymGenus("(indet)", null));
        assertTrue(SpecimenUploadParse.movesWithSynonymGenus("alw02", null));
        assertTrue(SpecimenUploadParse.movesWithSynonymGenus("a", null));
        assertTrue(SpecimenUploadParse.movesWithSynonymGenus("", null));
    }

    public void testNamesWithoutAGenusPart() throws Exception {
        assertEquals(Kind.NONE, decide(null).kind);
        assertEquals(Kind.NONE, decide("dorylinaephyracaces").kind);
        assertEquals("no genus row", Kind.NONE, decide("myrmicinaenogenus (indet)").kind);
        assertEquals("a subfamily placeholder in place of the genus", Kind.NONE, decide("(formicidae)(formicidae) (indet)").kind);
    }

    // ------------------------------------------------------------ 2. no subfamily: "(formicidae)genus ..."

    public void testPlaceholderSpeciesFollowsTheSpeciesRule() throws Exception {
        GenusSynonymRule.Result r = decide("(formicidae)pyramica ludovici");
        assertEquals("SAM-HYM-C003105", Kind.SPECIES_RULE, r.kind);
        assertEquals("myrmicinaestrumigenys ludovici", r.newName);
        assertEquals("myrmicinaepyramica ludovici", r.rebuiltName);
        assertEquals(CurrentValidNameRule.Kind.REPLACE, r.speciesRule.kind);
        assertTrue(r.placeholder);
    }

    public void testPlaceholderMorphoInASynonymGenus() throws Exception {
        GenusSynonymRule.Result r = decide("(formicidae)pyramica (indet)");
        assertEquals(Kind.GENUS_MOVE, r.kind);
        assertEquals("myrmicinaestrumigenys (indet)", r.newName);
        assertEquals("myrmicinaepyramica", r.genus);
        assertEquals("myrmicinaestrumigenys ma01", decide("(indet)pyramica ma01").newName);
    }

    public void testPlaceholderInAValidGenus() throws Exception {
        GenusSynonymRule.Result r = decide("(formicidae)strumigenys mg01");
        assertEquals(Kind.SUBFAMILY_PLACED, r.kind);
        assertEquals("myrmicinaestrumigenys mg01", r.newName);
        GenusSynonymRule.Result valid = decide("(formicidae)strumigenys emmae");
        assertEquals("a valid name stays as rebuilt", Kind.SUBFAMILY_PLACED, valid.kind);
        assertEquals("myrmicinaestrumigenys emmae", valid.newName);
        assertEquals("unknown species: left", Kind.NOT_IN_ANTCAT, decide("(formicidae)strumigenys zzunknown").kind);
        assertEquals("unknown species, synonym genus: left, listed", Kind.COMBINATION_NOT_IN_ANTCAT, decide("(formicidae)pyramica zzunknown").kind);
    }

    public void testPlaceholderGenusNotInAntcatOrAmbiguous() throws Exception {
        GenusSynonymRule.Result r = decide("(formicidae)stumigenys ludovici");
        assertEquals("a misspelling: never corrected by guess", Kind.GENUS_NOT_IN_ANTCAT, r.kind);
        assertNull(r.newName);
        assertEquals(Kind.GENUS_NOT_IN_ANTCAT, decide("(formicidae)polyrachis (indet)").kind);
        assertEquals("two subfamilies", Kind.GENUS_AMBIGUOUS, decide("(formicidae)twice ab01").kind);
        assertEquals("a genus of another status", Kind.NO_VALID_GENUS, decide("(formicidae)unavgenus ab01").kind);
        assertEquals("the species rule files it under a homonym placeholder: held", Kind.SHARED_NAME, decide("(formicidae)pyramica tohom").kind);
    }

    public void testHelpers() {
        assertEquals("dorylinaephyracaces", GenusSynonymRule.genusPart("dorylinaephyracaces (indet)"));
        assertNull(GenusSynonymRule.genusPart("dorylinaephyracaces"));
        assertEquals("pyramica", GenusSynonymRule.placeholderGenus("(formicidae)pyramica"));
        assertNull(GenusSynonymRule.placeholderGenus("myrmicinaepyramica"));
        assertNull(GenusSynonymRule.placeholderGenus("(formicidae)(formicidae)"));
        assertNull("not a placeholder", GenusSynonymRule.placeholderGenus("(ampulicinae)ampulex"));
        assertEquals("(indet)", GenusSynonymRule.speciesParts("dorylinaelioponera (indet)")[0]);
        assertNull(GenusSynonymRule.speciesParts("dorylinaelioponera (indet)")[1]);
        assertEquals("x", GenusSynonymRule.speciesParts("myrmicinaeallomerus mg01 x")[1]);
        assertEquals("dorylinaephyracaces (indet)",
            SpecimenUploadParse.uploadedNameBeforeGenusMove("dorylinaelioponera (indet)", new String[] {"dorylinaephyracaces", "dorylinaelioponera"}));
        assertNull(SpecimenUploadParse.uploadedNameBeforeGenusMove("dorylinaelioponera (indet)", null));
        assertNull(SpecimenUploadParse.uploadedNameBeforeGenusMove("dorylinaecerapachys (indet)", new String[] {"dorylinaephyracaces", "dorylinaelioponera"}));
    }

    // ------------------------------------------------------------ the nightly decision (second pass)

    public void testNightlyDecision() throws Exception {
        // Filed as uploaded: moves.
        String up = "dorylinaephyracaces (indet)";
        SpecimenNameUpdate.GenusDecision d = SpecimenNameUpdate.decideGenus(up, up, decide(up), decide(up), false);
        assertEquals(Outcome.CHANGE, d.outcome);
        assertEquals("dorylinaelioponera (indet)", d.newName);
        assertEquals("Uploaded As", up, d.newOriginalName);
        // Already moved (uploaded name kept): nothing to do.
        String cur = "dorylinaelioponera (indet)";
        assertEquals(Outcome.UNCHANGED, SpecimenNameUpdate.decideGenus(up, cur, decide(up), decide(cur), false).outcome);
        // Moved from a placeholder, then AntCat changes the name: the uploaded name leads.
        String pl = "(formicidae)pyramica ludovici";
        String old = "myrmicinaestrumigenys oldname";
        SpecimenNameUpdate.GenusDecision f = SpecimenNameUpdate.decideGenus(pl, old, decide(pl), decide(old), false);
        assertEquals(Outcome.CHANGE, f.outcome);
        assertEquals("myrmicinaestrumigenys ludovici", f.newName);
        assertEquals(pl, f.newOriginalName);
        // ... but never away from a name AntCat calls valid.
        assertEquals(Outcome.CURRENT_VALID, SpecimenNameUpdate.decideGenus(pl, "myrmicinaestrumigenys emmae", decide(pl), decide("myrmicinaestrumigenys emmae"), true).outcome);
        // What is left is counted under its own outcome.
        String bad = "(formicidae)stumigenys ludovici";
        assertEquals(Outcome.GENUS_NOT_IN_ANTCAT, SpecimenNameUpdate.decideGenus(bad, bad, decide(bad), decide(bad), false).outcome);
        assertEquals(Outcome.GENUS_AMBIGUOUS, SpecimenNameUpdate.decideGenus("(formicidae)twice ab01", "(formicidae)twice ab01", decide("(formicidae)twice ab01"), decide("(formicidae)twice ab01"), false).outcome);
        String unk = "myrmicinaewasmannia zzunknown";
        assertEquals(Outcome.COMBINATION_NOT_IN_ANTCAT, SpecimenNameUpdate.decideGenus(unk, unk, decide(unk), decide(unk), false).outcome);
        String unkValid = "(formicidae)strumigenys zzunknown";
        assertEquals(Outcome.SPECIES_NOT_IN_ANTCAT, SpecimenNameUpdate.decideGenus(unkValid, unkValid, decide(unkValid), decide(unkValid), false).outcome);
        assertEquals(Outcome.NO_VALID_GENUS, SpecimenNameUpdate.decideGenus("myrmicinaelost x1", "myrmicinaelost x1", decide("myrmicinaelost x1"), decide("myrmicinaelost x1"), false).outcome);
        assertEquals(Outcome.SHARED_NAME, SpecimenNameUpdate.decideGenus("(formicidae)pyramica tohom", "(formicidae)pyramica tohom", decide("(formicidae)pyramica tohom"), decide("(formicidae)pyramica tohom"), false).outcome);
        // A species filed under the valid genus by an upload (Phyracaces sp. not in AntCat): not moved back.
        String unkUp = "dorylinaephyracaces zzunknown";
        String unkCur = "dorylinaelioponera zzunknown";
        assertEquals(Outcome.UNCHANGED, SpecimenNameUpdate.decideGenus(unkUp, unkCur, decide(unkUp), decide(unkCur), false).outcome);
    }

    // ------------------------------------------------------------ reasons

    static NameUpdateReason.TaxonInfo genus(String name, Integer id, String history) {
        return new NameUpdateReason.TaxonInfo(name, null, id, AntcatHistory.parse(history));
    }

    public void testGenusReasonFromTheOldGenusHistory() {
        NameUpdateReason r = NameUpdateReason.genusSynonym(
            genus("Wasmannia", 101, "<div>Junior synonym of <i>Allomerus</i>: Longino &amp; Branstetter, 2026 PDF: 12.</div>"),
            genus("Allomerus", 102, null));
        assertEquals("genus synonym: Wasmannia is a junior synonym of Allomerus (Longino & Branstetter 2026)", r.text);
        assertEquals(Integer.valueOf(101), r.antcatId);
        // A later synonymy with another genus counts only for that genus (Phyracaces: Cerapachys, then Lioponera).
        NameUpdateReason p = NameUpdateReason.genusSynonym(
            genus("Phyracaces", 201, "<div>Junior synonym of <i>Cerapachys</i>: Brown, 1975 PDF: 4.</div><div>Junior synonym of <i>Lioponera</i>: Borowiec, 2016: 18.</div>"),
            genus("Lioponera", 202, null));
        assertEquals("genus synonym: Phyracaces is a junior synonym of Lioponera (Borowiec 2016)", p.text);
    }

    public void testGenusReasonFallbacks() {
        NameUpdateReason senior = NameUpdateReason.genusSynonym(genus("Oligomyrmex", 301, null),
            genus("Carebara", 302, "<div>Senior synonym of <i>Oligomyrmex</i>: Fernández, 2004 PDF: 191.</div>"));
        assertEquals("genus synonym: Oligomyrmex is a junior synonym of Carebara (Fernández 2004)", senior.text);
        assertEquals(Integer.valueOf(302), senior.antcatId);
        NameUpdateReason none = NameUpdateReason.genusSynonym(genus("Neoformica", 401, null), genus("Formica", 402, null));
        assertEquals("genus synonym: Neoformica is a junior synonym of Formica", none.text);
        assertEquals(Integer.valueOf(401), none.antcatId);
        NameUpdateReason placed = NameUpdateReason.subfamilyPlaced(genus("Strumigenys", 501, null), "myrmicinae");
        assertEquals("placed in its subfamily: Strumigenys is in Myrmicinae (the upload gave no subfamily)", placed.text);
    }

    public void testGenusReasonOnThePage() {
        String html = UploadNameReasons.reasonPart("genus synonym: Wasmannia is a junior synonym of Allomerus (Longino & Branstetter 2026)", 101,
            "myrmicinaewasmannia alw02", "myrmicinaewasmannia alw02", "myrmicinaeallomerus alw02");
        assertEquals(": <span class=\"name-update-reason\">genus synonym: <i>Wasmannia</i> is a junior synonym of <i>Allomerus</i>"
            + " (<a href=\"https://www.antcat.org/catalog/101\">Longino &amp; Branstetter 2026</a>)</span>", html);
    }
}

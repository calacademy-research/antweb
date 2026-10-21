package test.org.calacademy.antweb.upload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.upload.AntcatHistory;
import org.calacademy.antweb.upload.NameUpdateReason;
import org.calacademy.antweb.upload.NameUpdateReason.TaxonInfo;
import org.calacademy.antweb.util.HtmlSanitizer;

/**
 * Why the nightly name update moved a specimen (2026-10-20): AntCat's taxonomic history read by
 * AntcatHistory, the citation rule (the earliest act of the current kind after the most recent
 * contrary act, by date), and the reason of NameUpdateReason for each kind of change Brian Fisher
 * decided on (claude/antweb-name-update-reasons-spec.md: synonym, new combination, rank change,
 * nominotypical, gender agreement, misspelling, valid again), with real histories from the AntCat
 * export of 2026-10-20 (fixtures/antcat-history.tsv). Each history goes through HtmlSanitizer first, as the AntCat import
 * stores it (AntwebUpload.saveDescription).
 *
 * Run with -Dantweb.test=<the test/ folder> (default "test", the repository's).
 */
public class AntcatHistoryTest extends TestCase {

    static final class Row {
        final int antcatId;
        final String status;
        final String history;
        final String authorDate;
        Row(int antcatId, String status, String history, String authorDate) {
            this.antcatId = antcatId;
            this.status = status;
            this.history = history;
            this.authorDate = authorDate;
        }
    }

    static Map<String, Row> rows;

    static Map<String, Row> rows() throws IOException {
        if (rows != null) return rows;
        Path p = Paths.get(System.getProperty("antweb.test", "test"), "org/calacademy/antweb/upload/fixtures/antcat-history.tsv");
        Map<String, Row> m = new HashMap<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isBlank()) continue;
            String[] f = line.split("\t", -1);
            m.put(f[0], new Row(Integer.parseInt(f[1]), f[2], HtmlSanitizer.sanitize(f[3]), f.length > 4 ? f[4] : null));
        }
        rows = m;
        return m;
    }

    static AntcatHistory history(String name) throws IOException {
        Row r = rows().get(name);
        assertNotNull(name, r);
        return AntcatHistory.parse(r.history);
    }

    static TaxonInfo info(String name) throws IOException {
        Row r = rows().get(name);
        assertNotNull(name, r);
        return new TaxonInfo(name, r.status, r.antcatId, AntcatHistory.parse(r.history), r.authorDate);
    }

    static TaxonInfo info(String name, String status) {
        return new TaxonInfo(name, status, null, null);
    }

    static String reason(String oldName, String newName) throws IOException {
        return NameUpdateReason.of(null, info(oldName), info(newName)).text;
    }

    // ------------------------------------------------------------ the history

    public void testCrematogasterHespera() throws IOException {
        AntcatHistory h = history("Crematogaster hespera");
        assertNull("revived since: not a current synonymy", h.juniorSynonymRef("Crematogaster laeviuscula"));
        assertEquals("the former synonymy", "Morgan & Mackay, 2017", h.juniorSynonymRef(x -> x.endsWith("laeviuscula"), false));
        assertEquals("first status as species after the synonymy", "Ward & Blaimer, 2022", h.revivalRef(s -> s.endsWith("laeviuscula")));
        assertEquals("Blaimer, 2012c", h.combinationRef("Crematogaster"));
        assertNull("not its senior synonym", h.juniorSynonymRef(x -> x.endsWith("opaca"), false));

        // The same history as plain text, entries run together (the export's text with the tags removed).
        AntcatHistory t = AntcatHistory.parse("Taxonomic history Combination in Crematogaster (Crematogaster) : Blaimer, 2012c PDF : 55;"
            + " Ward & Blaimer, 2022 PDF : 902. Junior synonym of Crematogaster laeviuscula : Morgan & Mackay, 2017 PDF : 197"
            + " Status as species: Hunt & Snelling, 1975 PDF : 21; Smith, 1979 : 1379; Ward & Blaimer, 2022 PDF : 917.");
        assertEquals(3, t.getEntries().size());
        assertEquals("Morgan & Mackay, 2017", t.juniorSynonymRef(x -> x.endsWith("laeviuscula"), false));
        assertEquals("Ward & Blaimer, 2022", t.revivalRef(s -> true));
    }

    public void testCamponotusAtricolor() throws IOException {
        // Authors went back and forth: listed as a synonym of C. piceus until Radchenko 2016, as a species
        // from 1978 on. The synonymy cited is the first after the last listing as a species before it.
        AntcatHistory h = history("Camponotus atricolor");
        assertEquals("Radchenko, 2016", h.juniorSynonymRef(x -> x.endsWith("piceus"), false));
        assertEquals("Bernard, 1967a", h.juniorSynonymRef(x -> x.endsWith("merula"), false));
        assertNull("listed as a species after it: not current", h.juniorSynonymRef("Camponotus piceus"));
        assertEquals("Steiner et al., 2017", h.revivalRef(s -> s.endsWith("piceus")));
    }

    public void testDorymyrmexMedeis() throws IOException {
        // Revived after the synonymy with D. smithi (Snelling 1995a) by Deyrup 2003, not Deyrup 2017; the
        // later listing as a synonym of D. insanus (Cuezzo & Guerrero 2012) is another senior.
        AntcatHistory h = history("Dorymyrmex medeis");
        assertEquals("Snelling, 1995a", h.juniorSynonymRef(x -> x.endsWith("smithi"), false));
        assertEquals("Deyrup, 2003", h.revivalRef(s -> s.endsWith("smithi")));
    }

    public void testStrumigenysOhioensisAndHolcoponera() throws IOException {
        AntcatHistory h = history("Strumigenys ohioensis");
        assertEquals("Booher, 2019", h.juniorSynonymRef("Strumigenys pilinasis"));
        assertEquals("first Strumigenys after Pyramica", "Baroni Urbani & De Andrade, 2007", h.combinationRef("Strumigenys"));
        assertNull("in Strumigenys since: no current combination in Pyramica", h.combinationRef("Pyramica"));

        AntcatHistory s = history("Holcoponera striatula");
        assertEquals("genus with its author in the heading; after the last Gnamptogenys", "Camacho et al., 2022", s.combinationRef("Holcoponera"));
        assertNull("in Holcoponera since", s.combinationRef("Gnamptogenys"));
        assertNull(s.combinationRef("Camponotus"));
    }

    public void testNoHistory() throws IOException {
        AntcatHistory h = history("Opisthopsis pictus");   // a misspelling: no taxonomic history
        assertTrue(h.getEntries().isEmpty());
        assertNull(h.juniorSynonymRef("Opisthopsis picta"));
        assertNull(h.revivalRef(s -> true));
        assertTrue(AntcatHistory.parse(null).getEntries().isEmpty());
        assertEquals("Morgan & Mackay 2017", AntcatHistory.shortRef("Morgan & Mackay, 2017"));
        assertEquals("Snelling 1995a", AntcatHistory.shortRef("Snelling, 1995a"));
    }

    /** The citation rule: sorted by date, not by AntCat's order; 2025a before 2025b; earliest within an entry. */
    public void testCitationRuleDatesNotOrder() throws IOException {
        // Real: Lasius emarginatus lists "Combination in Acanthomyops: Donisthorpe 1927a; 1926b; 1950e" and,
        // after it, "Combination in Lasius: Stitz 1917 ..." (not in date order). Back in Lasius after the
        // last Acanthomyops (1950e): Wilson 1955a. Acanthomyops is not current (Lasius came after): none.
        AntcatHistory le = history("Lasius emarginatus");
        assertEquals("Wilson, 1955a", le.combinationRef("Lasius"));
        assertNull(le.combinationRef("Acanthomyops"));
        assertEquals("earliest of an entry by date", "Donisthorpe, 1926b", le.earliest(e -> e.subject.startsWith("Acanthomyops")));

        // Entries out of order, letters of one year, a revival between two synonymies.
        AntcatHistory a = AntcatHistory.parse("<div>Junior synonym of Pheidole alpha: Smith, 2025b PDF: 3; Jones, 2025a PDF: 9.</div>");
        assertEquals("2025a before 2025b", "Jones, 2025a", a.juniorSynonymRef("Pheidole alpha"));
        AntcatHistory t = AntcatHistory.parse("<div>Junior synonym of Pheidole beta: Young, 2025b PDF: 1; Old, 1950 PDF: 2.</div>"
            + "<div>Status as species: Brown, 2025a PDF: 4; Green, 1960: 5.</div>");
        assertEquals("after the revival of 2025a", "Young, 2025b", t.juniorSynonymRef("Pheidole beta"));
        AntcatHistory early = AntcatHistory.parse("<div>Junior synonym of Pheidole gamma: Old, 1950 PDF: 2.</div>"
            + "<div>Status as species: Brown, 1995b: 4.</div>");
        assertNull("the only synonymy predates the listing as a species: none", early.juniorSynonymRef("Pheidole gamma"));
        assertNull("a synonym again since 2025b: no current revival", t.revivalRef(s -> s.endsWith("beta")));
        AntcatHistory r = AntcatHistory.parse("<div>Junior synonym of Pheidole beta: Old, 1950 PDF: 2.</div>"
            + "<div>Status as species: Brown, 2025a PDF: 4; Green, 1960: 5.</div>");
        assertEquals("revived after the synonymy of 1950, though listed later", "Green, 1960", r.revivalRef(s -> s.endsWith("beta")));
    }

    // ------------------------------------------------------------ the reasons (claude/antweb-name-update-reasons-spec.md)

    /** 1. The epithet changes (beyond a gender ending): synonym, whatever the chain. */
    public void testSynonyms() throws IOException {
        assertEquals("the only synonymy (2001) predates a listing as a species (2010): no citation",
            "synonym: junior synonym of Liomyrmex gestroi", reason("Liomyrmex taylori", "Liomyrmex gestroi"));
        assertEquals("synonym: junior synonym of Lioponera clara (Brown 1975)", reason("Lioponera princeps", "Lioponera clara"));
        assertEquals("synonym: junior synonym of Melophorus bagoti (Heterick et al. 2017)", reason("Melophorus cowlei", "Melophorus bagoti"));
        assertEquals("synonym: junior synonym of Chelaner whitei (Heterick 2001)", reason("Chelaner armstrongi", "Chelaner whitei"));
        assertEquals("synonym: junior synonym of Camponotus maculatus (Baroni Urbani 1972)", reason("Camponotus cognatus", "Camponotus maculatus"));
        assertEquals("synonym: junior synonym of Camponotus yogi (Ward & Boudinot 2021)", reason("Camponotus apostemata", "Camponotus yogi"));
        assertEquals("synonym: junior synonym of Linepithema iniquum (Wild 2007a)", reason("Linepithema melleus", "Linepithema iniquum"));
        assertEquals("synonym: junior synonym of Alfaria simulans (Lattke & Delsinne 2016)", reason("Alfaria bufonis", "Alfaria simulans"));
        assertEquals("from the senior's history", "synonym: junior synonym of Centromyrmex brachycola (Branstetter & Longino 2022)",
            reason("Cryptopone mirabilis", "Centromyrmex brachycola"));
        assertEquals("synonym: junior synonym of Dolichoderus feae (Dill 2002)", reason("Dolichoderus fuscus", "Dolichoderus feae"));
        assertEquals("senior's history names it as a subspecies", "synonym: junior synonym of Pheidole pilifera (Wilson 2003a)",
            reason("Pheidole coloradensis", "Pheidole pilifera"));
        assertEquals("synonym: junior synonym of Pheidole bicarinata (Wilson 2003a)", reason("Pheidole vinelandica", "Pheidole bicarinata"));
        assertEquals("synonym: junior synonym of Temnothorax nevadensis (Ward 2005)", reason("Temnothorax lindae", "Temnothorax nevadensis"));
        assertEquals("synonym: junior synonym of Temnothorax nevadensis (Ward 2005)", reason("Temnothorax maryanae", "Temnothorax nevadensis"));
        assertEquals("synonym: junior synonym of Temnothorax nitens (Ward 2005)", reason("Temnothorax melinus", "Temnothorax nitens"));
        assertEquals("genus changed too", "synonym: junior synonym of Temnothorax nitens (Creighton 1950a)", reason("Leptothorax mariposa", "Temnothorax nitens"));
        assertEquals("synonym: junior synonym of Nesomyrmex asper (Arredondo & Guerrero 2025)", reason("Nesomyrmex tristani", "Nesomyrmex asper"));
        assertEquals("not a new combination (LaPolla et al. 2010a)", "synonym: junior synonym of Nylanderia guatemalensis (Williams et al. 2026)",
            reason("Paratrechina steinheili", "Nylanderia guatemalensis"));
        assertEquals("the latest synonymy after the revival", "synonym: junior synonym of Temnothorax sordidulus (Csősz et al. 2026)",
            reason("Temnothorax tergestinus", "Temnothorax sordidulus"));
    }

    /** 2. The genus changes, the epithet is the same or changes only its gender ending: new combination. */
    public void testCombinations() throws IOException {
        assertEquals("new combination: Nylanderia guatemalensis (LaPolla et al. 2010a)", reason("Paratrechina guatemalensis", "Nylanderia guatemalensis"));
        TaxonInfo gnamp = new TaxonInfo("Gnamptogenys striatula", Status.OBSOLETE_COMBINATION, 438134, null);
        NameUpdateReason r = NameUpdateReason.of(null, gnamp, info("Holcoponera striatula"));
        assertEquals("new combination: Holcoponera striatula (Camacho et al. 2022)", r.text);
        assertEquals("the history that gave the reference", Integer.valueOf(512958), r.antcatId);
        assertEquals("new combination: Allomerus sigmoideus (Longino & Branstetter 2026); spelling as in AntCat",
            reason("Wasmannia sigmoidea", "Allomerus sigmoideus"));
    }

    /** 3. Rank changes, cited from "Subspecies of" and "Status as species". */
    public void testRankChanges() throws IOException {
        assertEquals("raised to species: Crematogaster sjostedti (Taylor & McGavin 2020)", reason("Crematogaster gerstaeckeri sjostedti", "Crematogaster sjostedti"));
        assertEquals("raised to species: Dorylus burmeisteri (Schöning et al. 2026)", reason("Dorylus nigricans burmeisteri", "Dorylus burmeisteri"));
        assertEquals("raised to species: Formica dolosa (Trager et al. 2007)", reason("Formica schaufussi dolosa", "Formica dolosa"));
        assertEquals("placed as subspecies: Pheidole pubiventris foederalis (Kempf 1972b)", reason("Pheidole foederalis", "Pheidole pubiventris foederalis"));
    }

    /** 4. A b b -> A b: no act. A b b -> another species: the synonymy of A b. */
    public void testNominotypical() throws IOException {
        String n = "nominotypical subspecies, shown as the species";
        assertEquals(n, reason("Formica oreas oreas", "Formica oreas"));
        assertEquals(n, reason("Myrmica discontinua discontinua", "Myrmica discontinua"));
        assertEquals(n, reason("Prenolepis imparis imparis", "Prenolepis imparis"));
        // One wording for every synonym (Brian, 2026-10-20), the chain named after the reference.
        String texana = reason("Solenopsis texana texana", "Solenopsis canariensis");
        assertEquals("synonym: junior synonym of Solenopsis canariensis (Seifert 2026a); nominotypical subspecies of S. texana", texana);
        assertTrue(texana.startsWith("synonym: junior synonym of "));
        String html = NameUpdateReason.toHtml(texana, 7, "Solenopsis texana texana", "Solenopsis canariensis");
        assertEquals("synonym: junior synonym of <i>Solenopsis canariensis</i> (<a href=\"https://www.antcat.org/catalog/7\">Seifert 2026a</a>);"
            + " nominotypical subspecies of <i>S. texana</i>", html);
    }

    /** Unnecessary replacement names: junior objective synonyms, cited by the replacement's author. */
    public void testUnnecessaryReplacementName() throws IOException {
        assertEquals("unnecessary replacement name for L. reticulata (Brown 1975); junior objective synonym",
            reason("Lioponera lata", "Lioponera reticulata"));
        // AntCat's history of L. incontenta has only "Junior synonym of L. inconspicua: Clark, 1924b"; the senior's says
        // "Senior synonym of Lioponera incontenta (Brown, 1975) (unnecessary replacement name)".
        assertEquals("unnecessary replacement name for L. inconspicua (Brown 1975); junior objective synonym",
            reason("Lioponera incontenta", "Lioponera inconspicua"));
        TaxonInfo noAuthor = new TaxonInfo("Lioponera lata", Status.SYNONYM, 508832, history("Lioponera lata"));
        assertEquals("the author from the senior's entry", "unnecessary replacement name for L. reticulata (Brown 1975); junior objective synonym",
            NameUpdateReason.of(null, noAuthor, info("Lioponera reticulata")).text);
    }

    /**
     * 5. Only the ending of the epithet changes: AntWeb shows AntCat's spelling, never "misspelling", never a
     * gender claim (Brian Fisher, 2026-10-20: gender agreement is for AntCat to sort out).
     */
    public void testEndingOnly() throws IOException {
        assertEquals("unavailable misspelling", info("Leptanilloides nomada").status);
        assertEquals("spelling as in AntCat: Leptanilloides nomadus", reason("Leptanilloides nomada", "Leptanilloides nomadus"));
        assertEquals("spelling as in AntCat: Leptanilloides atlanticus", reason("Leptanilloides atlantica", "Leptanilloides atlanticus"));
        assertEquals("spelling as in AntCat: Opisthopsis diademata", reason("Opisthopsis diadematus", "Opisthopsis diademata"));
        assertEquals("spelling as in AntCat: Opisthopsis lienosa", reason("Opisthopsis lienosus", "Opisthopsis lienosa"));
        assertEquals("spelling as in AntCat: Opisthopsis picta", reason("Opisthopsis pictus", "Opisthopsis picta"));
        assertEquals("spelling as in AntCat: Lasius bombycinus", reason("Lasius bombycina", "Lasius bombycinus"));
        assertEquals("ending only, by stem", "spelling as in AntCat: Camponotus ligniperda", reason("Camponotus ligniperdus", "Camponotus ligniperda"));
        assertEquals("genus changed, AntCat's misspelling", "new combination: Fulakora celata (Ward & Fisher 2016); spelling as in AntCat",
            reason("Stigmatomma celata", "Fulakora celata"));
    }

    /** 6. Genuine misspellings: AntCat says misspelling and more than the ending differs. */
    public void testMisspellings() throws IOException {
        assertEquals("misspelling of Camponotus novaeboracensis", reason("Camponotus noveboracensis", "Camponotus novaeboracensis"));
    }

    /** 7. A misspelling hop inside a chain that changes the epithet: the synonymy. */
    public void testMisspellingInsideSynonymChain() throws IOException {
        assertEquals("synonym: junior synonym of Dorymyrmex flavus (Johnson 1989b)", reason("Dorymyrmex nigra", "Dorymyrmex flavus"));
        assertEquals("synonym: junior synonym of Linepithema iniquum (Wild 2007a)", reason("Iridomyrmex melleum", "Linepithema iniquum"));
        assertEquals("synonym: junior synonym of Pheidole indica (Sarnat et al. 2015)", reason("Pheidole teneriffana", "Pheidole indica"));
    }

    public void testValidAgain() throws IOException {
        NameUpdateReason r = NameUpdateReason.of(info("Crematogaster browni"), info("Crematogaster cerasi"), info("Crematogaster browni"));
        assertEquals("Your name Crematogaster browni is valid again. It had been shown under C. cerasi (synonymy by Morgan & Mackay 2017),"
            + " and was revived as a species by Ward & Blaimer 2022.", r.text);
        assertEquals(Integer.valueOf(435299), r.antcatId);
        r = NameUpdateReason.of(info("Dorymyrmex medeis"), info("Dorymyrmex smithi"), info("Dorymyrmex medeis"));
        assertEquals("Your name Dorymyrmex medeis is valid again. It had been shown under D. smithi (synonymy by Snelling 1995a),"
            + " and was revived as a species by Deyrup 2003.", r.text);
    }

    /** The same old -> new pair gets the same reason whatever name the group uploaded (2026-10-20 preview). */
    public void testDeterminism() throws IOException {
        TaxonInfo old = info("Colobopsis pylartes");
        TaxonInfo target = info("Colobopsis impressa");
        NameUpdateReason a = NameUpdateReason.of(info("Colobopsis pylartes"), old, target);
        NameUpdateReason b = NameUpdateReason.of(info("Camponotus pylartes"), old, target);   // obsolete combination
        assertEquals("synonym: junior synonym of Colobopsis impressa (Deyrup 2017)", a.text);
        assertEquals(a, b);

        TaxonInfo terg = info("Temnothorax tergestinus");
        TaxonInfo leptoTerg = new TaxonInfo("Leptothorax tergestinus", Status.OBSOLETE_COMBINATION, 999, null);
        assertEquals(NameUpdateReason.of(terg, terg, info("Temnothorax sordidulus")),
            NameUpdateReason.of(leptoTerg, terg, info("Temnothorax sordidulus")));
    }

    public void testRelationAndParts() {
        assertEquals(NameUpdateReason.Relation.ENDING, NameUpdateReason.relation("nomada", "nomadus"));
        assertEquals(NameUpdateReason.Relation.ENDING, NameUpdateReason.relation("nigra", "niger"));
        assertEquals(NameUpdateReason.Relation.ENDING, NameUpdateReason.relation("antarcticum", "antarcticus"));
        assertEquals(NameUpdateReason.Relation.ENDING, NameUpdateReason.relation("ligniperdus", "ligniperda"));
        assertEquals(NameUpdateReason.Relation.SPELLING, NameUpdateReason.relation("noveboracensis", "novaeboracensis"));
        assertEquals(NameUpdateReason.Relation.SPELLING, NameUpdateReason.relation("haenschei", "haenschi"));
        assertEquals(NameUpdateReason.Relation.OTHER, NameUpdateReason.relation("teneriffana", "indica"));
        assertArrayEquals(new String[] {"Formica", "picea"}, NameUpdateReason.parts("Formica picea Nylander, 1846"));
        assertArrayEquals(new String[] {"Camponotus", "pylartes"}, NameUpdateReason.parts("Camponotus (Colobopsis) pylartes"));
        assertArrayEquals(new String[] {"Colobopsis", "mayriella"},
            NameUpdateReason.parts("Colobopsis mayriella (unnecessary (second) replacement name for Colobopsis carinata Mayr, 1870)"));
        assertArrayEquals(new String[] {"Lioponera", "reticulata"}, NameUpdateReason.parts("Lioponera reticulata (Clark, 1926)"));
    }

    private static void assertArrayEquals(String[] a, String[] b) {
        assertEquals(java.util.Arrays.toString(a), java.util.Arrays.toString(b));
    }

    public void testNoReferenceIsLeftOut() {
        TaxonInfo syn = new TaxonInfo("Pheidole oldsyn", Status.SYNONYM, 99, null);
        NameUpdateReason r = NameUpdateReason.of(syn, syn, info("Pheidole newsen", Status.VALID));
        assertEquals("synonym: junior synonym of Pheidole newsen", r.text);
        assertEquals(Integer.valueOf(99), r.antcatId);

        TaxonInfo obs = new TaxonInfo("Pheidole oldcomb", Status.OBSOLETE_COMBINATION, 7, null);
        assertEquals("new combination: Camponotus oldcomb", NameUpdateReason.of(obs, obs, info("Camponotus oldcomb", Status.VALID)).text);

        TaxonInfo again = new TaxonInfo("Pheidole again", Status.VALID, 8, null);
        assertEquals("Your name Pheidole again is valid again. It had been shown under P. other, and was revived as a species.",
            NameUpdateReason.of(again, info("Pheidole other", Status.VALID), again).text);
        assertEquals("only the subgenus differs", "AntCat status: synonym",
            NameUpdateReason.of(syn, syn, info("Pheidole (Sub) oldsyn", Status.VALID)).text);
        assertNull("no id: none recorded", new NameUpdateReason("x", 0).antcatId);
    }

    public void testHtml() {
        String text = "Your name Crematogaster browni is valid again. It had been shown under C. cerasi (synonymy by Morgan & Mackay 2017),"
            + " and was revived as a species by Ward & Blaimer 2022.";
        String html = NameUpdateReason.toHtml(text, 435299, "Crematogaster browni", "Crematogaster cerasi", "Crematogaster browni");
        assertEquals("Your name <i>Crematogaster browni</i> is valid again. It had been shown under <i>C. cerasi</i> (synonymy by"
            + " <a href=\"https://www.antcat.org/catalog/435299\">Morgan &amp; Mackay 2017</a>), and was revived as a species by"
            + " <a href=\"https://www.antcat.org/catalog/435299\">Ward &amp; Blaimer 2022</a>.", html);

        assertEquals("new combination: <i>Holcoponera striatula</i> (<a href=\"https://www.antcat.org/catalog/512958\">Camacho et al. 2022</a>)",
            NameUpdateReason.toHtml("new combination: Holcoponera striatula (Camacho et al. 2022)", 512958, "Gnamptogenys striatula", "Holcoponera striatula"));
        assertEquals("no reference: a link to AntCat follows",
            "misspelling of <i>Camponotus novaeboracensis</i> (<a href=\"https://www.antcat.org/catalog/1\">AntCat</a>)",
            NameUpdateReason.toHtml("misspelling of Camponotus novaeboracensis", 1, "Camponotus noveboracensis", "Camponotus novaeboracensis"));
        assertEquals("abbreviated names in italics", "unnecessary replacement name for <i>L. reticulata</i> (<a href=\"https://www.antcat.org/catalog/2\">Brown 1975</a>);"
            + " junior objective synonym", NameUpdateReason.toHtml("unnecessary replacement name for L. reticulata (Brown 1975); junior objective synonym",
            2, "Lioponera lata", "Lioponera reticulata"));
        assertEquals("no id: no link", "AntCat status: unavailable", NameUpdateReason.toHtml("AntCat status: unavailable", null));
        assertFalse("escaped", NameUpdateReason.toHtml("synonym: junior synonym of <script>", 1, "<script>").contains("<script>"));
    }
}

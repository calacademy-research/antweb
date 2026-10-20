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
        Row(int antcatId, String status, String history) {
            this.antcatId = antcatId;
            this.status = status;
            this.history = history;
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
            m.put(f[0], new Row(Integer.parseInt(f[1]), f[2], HtmlSanitizer.sanitize(f[3])));
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
        return new TaxonInfo(name, r.status, r.antcatId, AntcatHistory.parse(r.history));
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
        assertEquals("Morgan & Mackay, 2017", h.juniorSynonymRef("Crematogaster laeviuscula"));
        assertEquals("subgenus and case do not matter", "Morgan & Mackay, 2017", h.juniorSynonymRef("crematogaster (Crematogaster) laeviuscula"));
        assertEquals("first status as species after the synonymy", "Ward & Blaimer, 2022", h.revivalRef(s -> s.endsWith("laeviuscula")));
        assertEquals("Blaimer, 2012c", h.combinationRef("Crematogaster"));
        assertNull("not its senior synonym", h.juniorSynonymRef("Crematogaster opaca"));

        // The same history as plain text, entries run together (the export's text with the tags removed).
        AntcatHistory t = AntcatHistory.parse("Taxonomic history Combination in Crematogaster (Crematogaster) : Blaimer, 2012c PDF : 55;"
            + " Ward & Blaimer, 2022 PDF : 902. Junior synonym of Crematogaster laeviuscula : Morgan & Mackay, 2017 PDF : 197"
            + " Status as species: Hunt & Snelling, 1975 PDF : 21; Smith, 1979 : 1379; Ward & Blaimer, 2022 PDF : 917.");
        assertEquals(3, t.getEntries().size());
        assertEquals("Morgan & Mackay, 2017", t.juniorSynonymRef("Crematogaster laeviuscula"));
        assertEquals("Ward & Blaimer, 2022", t.revivalRef(s -> true));
    }

    public void testCamponotusAtricolor() throws IOException {
        // Authors went back and forth: listed as a synonym of C. piceus until Radchenko 2016, as a species
        // from 1978 on. The synonymy cited is the first after the last listing as a species before it.
        AntcatHistory h = history("Camponotus atricolor");
        assertEquals("Radchenko, 2016", h.juniorSynonymRef("Camponotus piceus"));
        assertEquals("Bernard, 1967a", h.juniorSynonymRef("Camponotus merula"));
        assertEquals("Steiner et al., 2017", h.revivalRef(s -> s.endsWith("piceus")));
    }

    public void testDorymyrmexMedeis() throws IOException {
        // Revived after the synonymy with D. smithi (Snelling 1995a) by Deyrup 2003, not Deyrup 2017; the
        // later listing as a synonym of D. insanus (Cuezzo & Guerrero 2012) is another senior.
        AntcatHistory h = history("Dorymyrmex medeis");
        assertEquals("Snelling, 1995a", h.juniorSynonymRef("Dorymyrmex smithi"));
        assertEquals("Deyrup, 2003", h.revivalRef(s -> s.endsWith("smithi")));
    }

    public void testStrumigenysOhioensisAndHolcoponera() throws IOException {
        AntcatHistory h = history("Strumigenys ohioensis");
        assertEquals("Booher, 2019", h.juniorSynonymRef("Strumigenys pilinasis"));
        assertEquals("first Strumigenys after Pyramica", "Baroni Urbani & De Andrade, 2007", h.combinationRef("Strumigenys"));
        assertEquals("Bolton, 1999", h.combinationRef("Pyramica"));

        AntcatHistory s = history("Holcoponera striatula");
        assertEquals("genus with its author in the heading; after the last Gnamptogenys", "Camacho et al., 2022", s.combinationRef("Holcoponera"));
        assertEquals("Brown, 1958g", s.combinationRef("Gnamptogenys"));
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
        // last Acanthomyops (1950e): Wilson 1955a. In Acanthomyops after the last Lasius before it (1936): 1950e.
        AntcatHistory le = history("Lasius emarginatus");
        assertEquals("Wilson, 1955a", le.combinationRef("Lasius"));
        assertEquals("Donisthorpe, 1950e", le.combinationRef("Acanthomyops"));
        assertEquals("earliest of an entry by date", "Donisthorpe, 1926b", le.earliest(e -> e.subject.startsWith("Acanthomyops")));

        // Entries out of order, letters of one year, a revival between two synonymies.
        AntcatHistory a = AntcatHistory.parse("<div>Junior synonym of Pheidole alpha: Smith, 2025b PDF: 3; Jones, 2025a PDF: 9.</div>");
        assertEquals("2025a before 2025b", "Jones, 2025a", a.juniorSynonymRef("Pheidole alpha"));
        AntcatHistory t = AntcatHistory.parse("<div>Junior synonym of Pheidole beta: Young, 2025b PDF: 1; Old, 1950 PDF: 2.</div>"
            + "<div>Status as species: Brown, 2025a PDF: 4; Green, 1960: 5.</div>");
        assertEquals("after the revival of 2025a", "Young, 2025b", t.juniorSynonymRef("Pheidole beta"));
        assertEquals("revived after the synonymy of 1950, though listed later", "Green, 1960", t.revivalRef(s -> s.endsWith("beta")));
    }

    // ------------------------------------------------------------ the reasons (claude/antweb-name-update-reasons-spec.md)

    /** 1. The epithet changes (beyond a gender ending): synonym, whatever the chain. */
    public void testSynonyms() throws IOException {
        assertEquals("synonym: junior synonym of Lioponera inconspicua (Clark 1924b)", reason("Lioponera incontenta", "Lioponera inconspicua"));
        assertEquals("unnecessary replacement name: AntCat gives no reference", "synonym: junior synonym of Lioponera reticulata",
            reason("Lioponera lata", "Lioponera reticulata"));
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
        assertEquals("new combination: Allomerus sigmoideus (Longino & Branstetter 2026); ending agrees with the gender of Allomerus",
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
        assertEquals("synonym: Solenopsis texana is a junior synonym of Solenopsis canariensis (Seifert 2026a)",
            reason("Solenopsis texana texana", "Solenopsis canariensis"));
    }

    /** 5. Gender agreement (ICZN Art. 34.2), never "misspelling", even when AntCat's status says so. */
    public void testGenderAgreement() throws IOException {
        assertEquals("unavailable misspelling", info("Leptanilloides nomada").status);
        assertEquals("ending agrees with the gender of Leptanilloides", reason("Leptanilloides nomada", "Leptanilloides nomadus"));
        assertEquals("ending agrees with the gender of Leptanilloides", reason("Leptanilloides atlantica", "Leptanilloides atlanticus"));
        assertEquals("ending agrees with the gender of Opisthopsis", reason("Opisthopsis diadematus", "Opisthopsis diademata"));
        assertEquals("ending agrees with the gender of Opisthopsis", reason("Opisthopsis lienosus", "Opisthopsis lienosa"));
        assertEquals("ending agrees with the gender of Opisthopsis", reason("Opisthopsis pictus", "Opisthopsis picta"));
        assertEquals("ending agrees with the gender of Lasius", reason("Lasius bombycina", "Lasius bombycinus"));
        assertEquals("celata disagreed with neuter Stigmatomma", "new combination: Fulakora celata (Ward & Fisher 2016); ending agrees with the gender of Fulakora",
            reason("Stigmatomma celata", "Fulakora celata"));
    }

    /** 6. Genuine misspellings: ligniperda does not agree with masculine Camponotus (a noun, invariable). */
    public void testMisspellings() throws IOException {
        assertEquals("misspelling of Camponotus ligniperda", reason("Camponotus ligniperdus", "Camponotus ligniperda"));
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

    public void testRelationAndGender() {
        assertEquals(NameUpdateReason.Relation.GENDER, NameUpdateReason.relation("nomada", "nomadus"));
        assertEquals(NameUpdateReason.Relation.GENDER, NameUpdateReason.relation("nigra", "niger"));
        assertEquals(NameUpdateReason.Relation.GENDER, NameUpdateReason.relation("antarcticum", "antarcticus"));
        assertEquals(NameUpdateReason.Relation.SPELLING, NameUpdateReason.relation("noveboracensis", "novaeboracensis"));
        assertEquals(NameUpdateReason.Relation.SPELLING, NameUpdateReason.relation("haenschei", "haenschi"));
        assertEquals(NameUpdateReason.Relation.OTHER, NameUpdateReason.relation("teneriffana", "indica"));
        assertEquals(NameUpdateReason.Gender.MASCULINE, NameUpdateReason.genderOf("Camponotus"));
        assertEquals(NameUpdateReason.Gender.MASCULINE, NameUpdateReason.genderOf("Leptanilloides"));
        assertEquals(NameUpdateReason.Gender.FEMININE, NameUpdateReason.genderOf("Opisthopsis"));
        assertEquals(NameUpdateReason.Gender.NEUTER, NameUpdateReason.genderOf("Stigmatomma"));
        assertEquals(NameUpdateReason.Gender.NEUTER, NameUpdateReason.genderOf("Monomorium"));
        assertEquals(NameUpdateReason.Gender.MASCULINE, NameUpdateReason.genderOf("Chelaner"));
        assertArrayEquals(new String[] {"Formica", "picea"}, NameUpdateReason.parts("Formica picea Nylander, 1846"));
        assertArrayEquals(new String[] {"Camponotus", "pylartes"}, NameUpdateReason.parts("Camponotus (Colobopsis) pylartes"));
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
            "misspelling of <i>Camponotus ligniperda</i> (<a href=\"https://www.antcat.org/catalog/1\">AntCat</a>)",
            NameUpdateReason.toHtml("misspelling of Camponotus ligniperda", 1, "Camponotus ligniperdus", "Camponotus ligniperda"));
        assertEquals("no id: no link", "AntCat status: unavailable", NameUpdateReason.toHtml("AntCat status: unavailable", null));
        assertFalse("escaped", NameUpdateReason.toHtml("synonym: junior synonym of <script>", 1, "<script>").contains("<script>"));
    }
}

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
 * AntcatHistory, and the wording of NameUpdateReason for each case (synonym, new combination,
 * misspelling, valid again, other status), with real histories from the AntCat export of 2026-10-20
 * (fixtures/antcat-history.tsv). Each history goes through HtmlSanitizer first, as the AntCat import
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

    // ------------------------------------------------------------ the history

    public void testCrematogasterHespera() throws IOException {
        AntcatHistory h = history("Crematogaster hespera");
        assertEquals("Morgan & Mackay, 2017", h.juniorSynonymRef("Crematogaster laeviuscula"));
        assertEquals("subgenus and case do not matter", "Morgan & Mackay, 2017", h.juniorSynonymRef("crematogaster (Crematogaster) laeviuscula"));
        assertEquals("latest status as species", "Ward & Blaimer, 2022", h.statusAsSpeciesRef());
        assertEquals("Ward & Blaimer, 2022", h.combinationRef("Crematogaster"));
        assertNull("not its senior synonym", h.juniorSynonymRef("Crematogaster opaca"));

        // The same history as plain text, entries run together (the export's text with the tags removed).
        AntcatHistory t = AntcatHistory.parse("Taxonomic history Combination in Crematogaster (Crematogaster) : Blaimer, 2012c PDF : 55;"
            + " Ward & Blaimer, 2022 PDF : 902. Junior synonym of Crematogaster laeviuscula : Morgan & Mackay, 2017 PDF : 197"
            + " Status as species: Hunt & Snelling, 1975 PDF : 21; Smith, 1979 : 1379; Ward & Blaimer, 2022 PDF : 917.");
        assertEquals(3, t.getEntries().size());
        assertEquals("Morgan & Mackay, 2017", t.juniorSynonymRef("Crematogaster laeviuscula"));
        assertEquals("Ward & Blaimer, 2022", t.statusAsSpeciesRef());
    }

    public void testCamponotusAtricolor() throws IOException {
        AntcatHistory h = history("Camponotus atricolor");
        assertEquals("first reference of the entry", "Dalla Torre, 1893", h.juniorSynonymRef("Camponotus piceus"));
        assertEquals("Mayr, 1855", h.juniorSynonymRef("Camponotus lateralis"));
        assertEquals("Bernard, 1967a", h.juniorSynonymRef("Camponotus merula"));
        assertEquals("Borowiec & Salata, 2022", h.statusAsSpeciesRef());
    }

    public void testDorymyrmexMedeis() throws IOException {
        AntcatHistory h = history("Dorymyrmex medeis");
        assertEquals("Snelling, 1995a", h.juniorSynonymRef("Dorymyrmex smithi"));
        assertEquals("Johnson, 1989b", h.juniorSynonymRef("Dorymyrmex insanus"));
        assertEquals("Deyrup, 2017", h.statusAsSpeciesRef());
    }

    public void testStrumigenysOhioensisAndHolcoponera() throws IOException {
        AntcatHistory h = history("Strumigenys ohioensis");
        assertEquals("Booher, 2019", h.juniorSynonymRef("Strumigenys pilinasis"));
        assertEquals("the entry without subgenus is the latest", "Baroni Urbani & De Andrade, 2007", h.combinationRef("Strumigenys"));
        assertEquals("Bolton, 1999", h.combinationRef("Pyramica"));

        AntcatHistory s = history("Holcoponera striatula");
        assertEquals("genus with its author in the heading; latest reference", "Camacho et al., 2022", s.combinationRef("Holcoponera"));
        assertEquals("Brown, 1958g", s.combinationRef("Gnamptogenys"));
        assertNull(s.combinationRef("Camponotus"));
    }

    public void testNoHistory() throws IOException {
        AntcatHistory h = history("Opisthopsis pictus");   // a misspelling: no taxonomic history
        assertTrue(h.getEntries().isEmpty());
        assertNull(h.juniorSynonymRef("Opisthopsis picta"));
        assertNull(h.statusAsSpeciesRef());
        assertTrue(AntcatHistory.parse(null).getEntries().isEmpty());
        assertEquals("Morgan & Mackay 2017", AntcatHistory.shortRef("Morgan & Mackay, 2017"));
        assertEquals("Snelling 1995a", AntcatHistory.shortRef("Snelling, 1995a"));
    }

    // ------------------------------------------------------------ the wording

    public void testWordingPerCase() throws IOException {
        // Valid again: specimens uploaded as C. hespera, filed under C. laeviuscula, go back.
        NameUpdateReason r = NameUpdateReason.of(info("Crematogaster hespera"), info("Crematogaster laeviuscula", Status.VALID), info("Crematogaster hespera"));
        assertEquals("uploaded as Crematogaster hespera; valid again (was junior synonym of Crematogaster laeviuscula,"
            + " Morgan & Mackay 2017; status as species: Ward & Blaimer 2022)", r.text);
        assertEquals(Integer.valueOf(435560), r.antcatId);

        r = NameUpdateReason.of(info("Dorymyrmex medeis"), info("Dorymyrmex smithi", Status.VALID), info("Dorymyrmex medeis"));
        assertEquals("uploaded as Dorymyrmex medeis; valid again (was junior synonym of Dorymyrmex smithi, Snelling 1995a;"
            + " status as species: Deyrup 2017)", r.text);

        // Synonym, from the uploaded name's history.
        TaxonInfo ohio = info("Strumigenys ohioensis");
        r = NameUpdateReason.of(ohio, ohio, info("Strumigenys pilinasis"));
        assertEquals("synonym: junior synonym of Strumigenys pilinasis (Booher 2019)", r.text);
        assertEquals(Integer.valueOf(448891), r.antcatId);

        // Obsolete combination: the reference is in the new name's history.
        TaxonInfo gnamp = new TaxonInfo("Gnamptogenys striatula", Status.OBSOLETE_COMBINATION, 438134, null);
        r = NameUpdateReason.of(gnamp, gnamp, info("Holcoponera striatula"));
        assertEquals("new combination: Holcoponera striatula (Camacho et al. 2022)", r.text);
        assertEquals(Integer.valueOf(512958), r.antcatId);

        // Misspelling.
        TaxonInfo pictus = info("Opisthopsis pictus");
        r = NameUpdateReason.of(pictus, pictus, info("Opisthopsis picta"));
        assertEquals("misspelling of Opisthopsis picta", r.text);
        assertEquals(Integer.valueOf(513614), r.antcatId);

        // Any other status.
        TaxonInfo other = new TaxonInfo("Pheidole other", "unavailable uncategorized", 1234, null);
        r = NameUpdateReason.of(other, other, info("Pheidole third", Status.VALID));
        assertEquals("AntCat status: unavailable uncategorized", r.text);
        assertEquals(Integer.valueOf(1234), r.antcatId);
    }

    public void testNoReferenceIsLeftOut() {
        TaxonInfo syn = new TaxonInfo("Tapinoma indicum", Status.SYNONYM, 99, null);
        NameUpdateReason r = NameUpdateReason.of(syn, syn, info("Ravavy indicus", Status.VALID));
        assertEquals("synonym: junior synonym of Ravavy indicus", r.text);
        assertEquals(Integer.valueOf(99), r.antcatId);

        TaxonInfo obs = new TaxonInfo("Pheidole oldcomb", Status.OBSOLETE_COMBINATION, 7, null);
        assertEquals("new combination: Pheidole newcomb", NameUpdateReason.of(obs, obs, info("Pheidole newcomb", Status.VALID)).text);

        TaxonInfo again = new TaxonInfo("Pheidole again", Status.VALID, 8, null);
        assertEquals("uploaded as Pheidole again; valid again (was junior synonym of Pheidole other)",
            NameUpdateReason.of(again, info("Pheidole other", Status.VALID), again).text);
        assertNull("no id: none recorded", new NameUpdateReason("x", 0).antcatId);
    }

    public void testHtml() {
        String text = "uploaded as Crematogaster hespera; valid again (was junior synonym of Crematogaster laeviuscula,"
            + " Morgan & Mackay 2017; status as species: Ward & Blaimer 2022)";
        String html = NameUpdateReason.toHtml(text, 435560, "Crematogaster hespera", "Crematogaster laeviuscula", "Crematogaster hespera");
        assertEquals("uploaded as <i>Crematogaster hespera</i>; valid again (was junior synonym of <i>Crematogaster laeviuscula</i>,"
            + " <a href=\"https://www.antcat.org/catalog/435560\">Morgan &amp; Mackay 2017</a>; status as species:"
            + " <a href=\"https://www.antcat.org/catalog/435560\">Ward &amp; Blaimer 2022</a>)", html);

        assertEquals("new combination: <i>Holcoponera striatula</i> (<a href=\"https://www.antcat.org/catalog/512958\">Camacho et al. 2022</a>)",
            NameUpdateReason.toHtml("new combination: Holcoponera striatula (Camacho et al. 2022)", 512958, "Gnamptogenys striatula", "Holcoponera striatula"));
        assertEquals("no reference: a link to AntCat follows",
            "misspelling of <i>Opisthopsis picta</i> (<a href=\"https://www.antcat.org/catalog/513614\">AntCat</a>)",
            NameUpdateReason.toHtml("misspelling of Opisthopsis picta", 513614, "Opisthopsis pictus", "Opisthopsis picta"));
        assertEquals("no id: no link", "AntCat status: unavailable", NameUpdateReason.toHtml("AntCat status: unavailable", null));
        assertFalse("escaped", NameUpdateReason.toHtml("synonym: junior synonym of <script>", 1, "<script>").contains("<script>"));
    }
}

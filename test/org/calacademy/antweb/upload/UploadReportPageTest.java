package test.org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.upload.UploadNotice;
import org.calacademy.antweb.upload.UploadReportPage;
import org.calacademy.antweb.util.Html;

/**
 * The upload report page (UploadReportPage, Brian Fisher 2026-10-29: "I really dislike how our specimen reports
 * look"), without a database: rendered from fixtures modelled on real reports (EcoFoG, 2026-10-29; a CAS TaxonWorks
 * nightly upload) and one that fills every remaining section.
 *
 * The summary's counts are the email's counts (UploadNotice.attentionSummary, MessageMgr.getNotLoadedCount,
 * getCorrectedCount, getRedFlagCount): one rule, MessageMgr.attention. Sections appear only when they found
 * something, in the group of their kind; a long section shows its first rows and the rest under "Show all"; checks
 * that found nothing are one collapsed line; every row links its lines to the uploaded file (logMgr.do uploadLog).
 */
public class UploadReportPageTest extends TestCase {

    static final String APP = "https://www.antweb.org";

    /** A report fixture: what the page shows besides the sections, and the sections. */
    public static final class Fixture {
        public final UploadReportPage.Facts facts = new UploadReportPage.Facts();
        public final MessageMgr mgr = new MessageMgr();
        public final Group group;

        Fixture(int groupId, String groupName, String file, String operation) {
            group = new Group(groupId);
            group.setName(groupName);
            facts.domainApp = APP;
            facts.cssHref = APP + "/common/tools.css";
            facts.operation = operation;
            facts.title = "20261029-14:02:11-" + operation + ".html";
            facts.uploadFileName = file;
            facts.uploadedFileHtml = "<a href=\"" + APP + "/uploadFile.do?file=" + Html.escAttr(Html.url(file)) + "\">upload/" + Html.esc(file) + "</a>";
            facts.logFileHtml = "<a href=\"" + APP + "/web/log/specimen/" + Html.escAttr(facts.title) + "\">specimen/" + Html.esc(facts.title) + "</a>";
            facts.groupHtml = "<a href='" + APP + "/group.do?id=" + groupId + "'>" + Html.esc(groupName) + "</a>";
            facts.encoding = "UTF-8";
            facts.version = "9.2.0";
            facts.countsRule = "2026-10-27";
        }

        /** The line of the file being read (the specimen upload sets it, then the code). */
        void at(int line, String code) {
            mgr.setCurrentLine(line);
            mgr.setCurrentCode(code);
        }

        void done() {
            mgr.setCurrentLine(0);
            mgr.setCurrentCode(null);
        }

        public String render() {
            mgr.compileMessages(group);
            return UploadReportPage.render(facts, mgr);
        }

        /** The email's facts for this upload, as SpecimenUploadJob and UploadAction fill them. */
        UploadNotice.Facts email() {
            UploadNotice.Facts f = new UploadNotice.Facts();
            f.complete = true;
            f.notLoaded = mgr.getNotLoadedCount();
            f.corrected = mgr.getCorrectedCount();
            f.redFlagged = mgr.getRedFlagCount();
            f.specimensLoaded = facts.insertedSpecimens;
            return f;
        }
    }

    static String adm1(String name, int groupId) {
        return "<a href='" + Html.escAttr(APP + "/adm1Mgr.do?adm1Name=" + Html.url(name) + "&countryName=French+Guiana&groupId=" + groupId) + "'>" + Html.esc(name) + "</a>";
    }

    static String taxon(String taxonName, String display) {
        return "<a href='" + APP + "/description.do?taxonName=" + Html.url(taxonName) + "'>" + Html.esc(display) + "</a>";
    }

    /** EcoFoG's specimen upload of 2026-10-29 (Upload ID 2267): 959 lines read, 945 loaded. */
    public static Fixture ecofog() {
        Fixture x = new Fixture(62, "EcoFoG", "20261029-14:02:11-specimen62.txt", "specimenUpload");
        UploadReportPage.Facts f = x.facts;
        f.uploadId = 2267;
        f.date = "Thu Oct 29 14:09:40 PDT 2026";
        f.curatorHtml = "<a href='" + APP + "/curator.do?id=301'>Fr&eacute;d&eacute;ric Petitclerc</a>";
        f.uploadedBy = "Upload page (manual)";
        f.recordCount = 959;
        f.parsed = 947;
        f.processed = 945;
        f.insertedSpecimens = 945;
        f.museumsHtml = "<span title='{ECOFOG=945}'>1</span>";
        f.execTime = "6 min";
        f.insertedSpecies = 3;
        f.updatedSpecies = 128;

        int line = 2;
        String[][] adm1s = {{"Saint-Laurent-du-Maroni", "41"}, {"Regina", "18"}, {"Saül", "12"}, {"Kourou", "64"},
            {"Roura", "22"}, {"Camopi", "9"}, {"Maripasoula", "6"}};
        for (String[] a : adm1s) {
            for (int i = 0; i < Integer.parseInt(a[1]); i++) {
                x.at(line, String.format("ecofog-boum-%04d-01", line));
                x.mgr.addToMessages(MessageMgr.notValidAdm1, adm1(a[0], 62));
                line += 3;
            }
        }
        x.at(412, "ecofog-boum-0412-01");
        x.mgr.addToMessages(MessageMgr.countryMissing);
        x.mgr.addToMessages(MessageMgr.correctedBioregion, Html.esc("Using Neotropical for country:French Guiana"),
            "code:ecofog-boum-0412-01 country:French Guiana adm1:null useBioregion:Neotropical listedBioregion:null");
        x.at(926, null);
        x.mgr.addToMessages(MessageMgr.codeNotFound, "line:926 code:");
        x.at(959, null);
        x.mgr.addToMessages(MessageMgr.codeNotFound, "line:959 code:");
        x.at(381, "ecofog-boum-0381-01");
        x.mgr.addToMessages(MessageMgr.genusNotInAntcat, Html.esc("Myrmicinae (subfamily ecofog-boum-0381-01)"));
        x.at(77, "ecofog-boum-0077-02");
        x.mgr.addToMessages(MessageMgr.genusNotInAntcat, Html.esc("Ca (no subfamily)"));
        for (int i = 0; i < 17; i++) {
            x.at(100 + i * 7, null);
            x.mgr.flag("is_introduced");
        }
        x.mgr.flag("noCasteNotes");
        x.mgr.flag("noCasteNotes");
        x.done();
        return x;
    }

    static final String[] HOLDERS = {"Code already in AntWeb, uploaded by UC Davis (Ward lab)", "Code already in AntWeb, uploaded by JTLC",
        "Code already in AntWeb, uploaded by MCZ", "Code already in AntWeb for this group (given twice in the file?)"};

    static final String[][] SYNONYMS = {
        {"camponotus maculatus pallidus", "Camponotus maculatus pallidus", "camponotus pallidus", "Camponotus pallidus", "status revived as species (Rakotonirina, 2016)"},
        {"pheidole longispinosa", "Pheidole longispinosa", "pheidole fervens", "Pheidole fervens", "junior synonym of Pheidole fervens (Eguchi, 2008)"},
        {"tetramorium simillimum", "Tetramorium simillimum", "tetramorium simillimum", "Tetramorium simillimum", "obsolete combination of Tetramorium simillimum"},
    };

    /** A CAS TaxonWorks nightly upload: about 268,000 lines, the usual sections at their usual sizes. */
    public static Fixture casNightly() {
        Fixture x = new Fixture(1, "California Academy of Sciences", "20261029-05:31:02-taxonworks-cas.txt", "taxonWorksUpload");
        UploadReportPage.Facts f = x.facts;
        f.uploadId = 2266;
        f.date = "Thu Oct 29 07:12:55 PDT 2026";
        f.curatorHtml = "<a href='" + APP + "/curator.do?id=1'>Brian Fisher</a>";
        f.uploadedBy = "Nightly TaxonWorks job (automatic)";
        f.recordCount = 268_214;
        f.parsed = 268_132;
        f.processed = 267_861;
        f.insertedSpecimens = 267_581;
        f.museumsHtml = "<span title='{CASC=241002, MHNG=9050, NHMB=4120}'>14</span>";
        f.execTime = "1 hr 41 min";
        f.insertedSpecies = 21;
        f.updatedSpecies = 18_914;

        int line = 1200;
        // Duplicate Entries (not uploaded): 280 codes, held by other groups (and a few given twice).
        int[] perHolder = {171, 64, 33, 12};
        for (int h = 0; h < HOLDERS.length; h++) {
            for (int i = 0; i < perHolder[h]; i++) {
                line += 517 + (i * 37) % 300;
                x.at(line, String.format("casent%07d", 100000 + line % 9000000));
                x.mgr.addToMessages(MessageMgr.duplicateEntries, Html.esc(HOLDERS[h]).replace("&#39;", "'"), "Specimen code:x");
            }
        }
        // Recognized invalid species: dozens of names, each with its reason.
        String[] genera = {"Camponotus", "Pheidole", "Strumigenys", "Tetramorium", "Hypoponera", "Crematogaster", "Nesomyrmex", "Monomorium"};
        for (int i = 0; i < 46; i++) {
            String[] s = SYNONYMS[i % SYNONYMS.length];
            String g = genera[i % genera.length];
            String oldName = g + " " + s[1].split(" ")[1] + (i < 3 ? "" : "-" + i);
            String newName = g + " " + s[3].split(" ")[1] + (i < 3 ? "" : "-" + i);
            String value = taxon(oldName.toLowerCase(), oldName) + " -> " + taxon(newName.toLowerCase(), newName)
                + ": <span class=\"name-update-reason\">" + Html.esc(s[4].replace(s[3], newName).replace(s[1], oldName)) + "</span>";
            for (int k = 0; k <= i % 4; k++) {
                line += 211;
                x.at(line, String.format("casent%07d", 300000 + line % 900000));
                x.mgr.addToMessages(MessageMgr.recognizedInvalidSpecies, value);
            }
        }
        // Names not in AntCat (kept as uploaded).
        for (int i = 0; i < 31; i++) {
            String name = genera[i % genera.length] + " " + (i % 2 == 0 ? "mg" : "bfm") + String.format("%02d", i + 1);
            for (int k = 0; k < 1 + i % 6; k++) {
                line += 97;
                x.at(line, String.format("casent%07d", 500000 + line % 900000));
                x.mgr.addToMessages(MessageMgr.unrecognizedInvalidSpecies, taxon(name.toLowerCase(), name));
            }
        }
        // adm1 not valid.
        String[] adm1s = {"Toliara", "Antsiranana Prov.", "Fianarantsoa Province", "Mahajanga ", "Prov. Toamasina", "Antananarivo Province"};
        for (int i = 0; i < adm1s.length; i++) {
            for (int k = 0; k < 2 + i * 9; k++) {
                line += 41;
                x.at(line, String.format("casent%07d", 700000 + line % 900000));
                x.mgr.addToMessages(MessageMgr.notValidAdm1, adm1(adm1s[i], 1).replace("French+Guiana", "Madagascar"));
            }
        }
        // Identified only to genus: filed as indet.
        for (int i = 0; i < 64; i++) {
            String g = genera[i % genera.length] + (i < genera.length ? "" : String.valueOf((char) ('a' + i / genera.length)));
            for (int k = 0; k < 1 + (i * 13) % 40; k++) {
                line += 3;
                x.at(line, String.format("casent%07d", 800000 + line % 900000));
                x.mgr.addToMessages(MessageMgr.identifiedOnlyToGenus, taxon(g.toLowerCase() + " (indet)", g + " (indet)"));
            }
        }
        // Current valid name not in AntWeb yet: kept.
        String[][] missing = {{"Strumigenys rogeri", "Strumigenys emmae"}, {"Camponotus quadrimaculatus", "Camponotus sexpunctatus"},
            {"Pheidole megacephala", "Pheidole megacephala melancholica"}, {"Hypoponera punctatissima", "Hypoponera ergatandria"}};
        for (String[] m : missing) {
            line += 803;
            x.at(line, String.format("casent%07d", 900000 + line % 90000));
            x.mgr.addToMessages(MessageMgr.currentValidNameNotInAntweb, taxon(m[0].toLowerCase(), m[0]) + " -> " + Html.esc(m[1]) + " (not in AntWeb yet)");
        }
        // Dates AntWeb could not read (corrected: loaded without the date).
        for (String d : new String[] {"1998-13-04", "31/02/2003", "2011-00-00 to 2011-00-00"}) {
            line += 1999;
            x.at(line, String.format("casent%07d", 950000 + line % 9000));
            x.mgr.addToMessages(MessageMgr.invalidDateCollectedStart, Html.esc(d));
        }
        // Red-flagged: coordinates outside the country.
        for (int i = 0; i < 3; i++) {
            line += 4001;
            String code = String.format("casent%07d", 970000 + i);
            x.at(line, code);
            x.mgr.addToMessages(MessageMgr.latLonNotInCountryBounds, "<a href='" + APP + "/specimen.do?code=" + code + "'>" + code + "</a>",
                "code:" + code + " lat:-12.3 lon:52.1 country:Madagascar");
        }
        x.done();
        return x;
    }

    static final Set<String> MAP_KEYS = new HashSet<>(Arrays.asList(MessageMgr.invalidSubfamilyMap, MessageMgr.invalidSubfamilyForGenusReplaced,
        MessageMgr.subfamilyGenusComboNotInBolton, MessageMgr.genusOutsideNativeBioregion, MessageMgr.badQuotations,
        MessageMgr.duplicatedRecord, MessageMgr.preferredSubfamilyForGenusReplaced));

    /** One line in each section the other two fixtures do not use (a species list style upload: lines not linked). */
    public static Fixture everyOtherSection() {
        Fixture x = new Fixture(9999, "ZZ Test Group <staging>", "20261029-15:00:00-specimen9999.txt", "specimenUpload");
        UploadReportPage.Facts f = x.facts;
        f.uploadId = 2268;
        f.date = "Thu Oct 29 15:01:10 PDT 2026";
        f.curatorHtml = "<a href='" + APP + "/curator.do?id=9'>Test Uploader</a>";
        f.uploadedBy = "Upload page (manual)";
        f.recordCount = 120;
        f.parsed = 118;
        f.processed = 110;
        f.insertedSpecimens = 104;
        f.execTime = "4 sec";
        Set<String> used = USED;
        int line = 2;
        for (String key : x.mgr.attentionByKey().keySet()) {
            if (used.contains(key)) continue;
            x.at(line, String.format("zztest%04d", line));
            if (MAP_KEYS.contains(key)) {
                x.mgr.addToMapMessages(key, Html.esc("Myrmicinae Foo"), Html.esc("value of " + key));
            } else {
                x.mgr.addToMessages(key, Html.esc("sample " + key + " <b>&"));
            }
            line += 1;
        }
        x.done();
        return x;
    }

    /** The sections the EcoFoG and CAS fixtures fill. */
    static final Set<String> USED = new HashSet<>(Arrays.asList(MessageMgr.notValidAdm1, MessageMgr.countryMissing, MessageMgr.correctedBioregion,
            MessageMgr.codeNotFound, MessageMgr.genusNotInAntcat, MessageMgr.duplicateEntries, MessageMgr.recognizedInvalidSpecies,
            MessageMgr.unrecognizedInvalidSpecies, MessageMgr.identifiedOnlyToGenus, MessageMgr.currentValidNameNotInAntweb,
            MessageMgr.invalidDateCollectedStart, MessageMgr.latLonNotInCountryBounds));

    /** An upload that found nothing. */
    public static Fixture emptyRun() {
        Fixture x = new Fixture(9999, "ZZ Test Group", "20261029-16:00:00-specimen9999.txt", "specimenUpload");
        x.facts.uploadId = 2269;
        x.facts.date = "Thu Oct 29 16:00:30 PDT 2026";
        x.facts.recordCount = 5;
        x.facts.parsed = 5;
        x.facts.processed = 5;
        x.facts.insertedSpecimens = 5;
        return x;
    }

    // --------------------------------------------------------------------------------------------- helpers

    static int count(String page, String id) {
        Matcher m = Pattern.compile("id=\"rep-count-" + id + "\" data-count=\"(\\d+)\"").matcher(page);
        assertTrue("no tile " + id, m.find());
        return Integer.parseInt(m.group(1));
    }

    /** data-key -> data-kind of each section of the page. */
    static List<String[]> sections(String page) {
        List<String[]> list = new ArrayList<>();
        Matcher m = Pattern.compile("<section class=\"aw-card rep-sec\" id=\"sec-[^\"]*\" data-key=\"([^\"]*)\" data-kind=\"([^\"]*)\" data-total=\"(\\d+)\" data-rows=\"(\\d+)\"").matcher(page);
        while (m.find()) list.add(new String[] {m.group(1), m.group(2), m.group(3), m.group(4)});
        return list;
    }

    static int occurrences(String s, String sub) {
        return s.split(Pattern.quote(sub), -1).length - 1;
    }

    /** The summary's counts equal the email's, and its sentence is the email's sentence. */
    static void assertSummaryIsTheEmails(Fixture x, String page) {
        UploadNotice.Facts email = x.email();
        assertEquals(email.notLoaded, count(page, "notLoaded"));
        assertEquals(email.corrected, count(page, "corrected"));
        assertEquals(email.redFlagged, count(page, "redFlagged"));
        assertEquals(x.mgr.getNoteCount(), count(page, "notes"));
        assertEquals(x.facts.recordCount, count(page, "read"));
        assertEquals(x.facts.insertedSpecimens, count(page, "loaded"));
        String sentence = UploadNotice.attentionSummary(email);
        assertNotNull(sentence);
        assertTrue(page, page.contains("<p class=\"rep-sentence\" id=\"rep-sentence\">" + Html.esc(sentence) + "</p>"));
        // Each section is in the group of its kind, and the groups add up to the tiles.
        int notLoaded = 0, corrected = 0;
        for (String[] s : sections(page)) {
            if (MessageMgr.NOT_LOADED.equals(s[1])) notLoaded += Integer.parseInt(s[2]);
            if (MessageMgr.CORRECTED_KIND.equals(s[1])) corrected += Integer.parseInt(s[2]);
            String attention = x.mgr.attentionByKey().get(s[0]);
            if (attention != null) assertEquals(s[0], attention, s[1]);
        }
        assertEquals(email.notLoaded, notLoaded);
        assertEquals(email.corrected, corrected);
        assertTilesAreSectionSums(page);
    }

    /** Each tile (not loaded, corrected, red-flagged, notes) is the sum of the data-total of its group's sections
     *  (2026-10-30), and each group's pill says that sum. */
    static void assertTilesAreSectionSums(String page) {
        String[][] tiles = {{"notLoaded", MessageMgr.NOT_LOADED}, {"corrected", MessageMgr.CORRECTED_KIND},
            {"redFlagged", MessageMgr.RED_FLAG}, {"notes", MessageMgr.NOTE}};
        for (String[] t : tiles) {
            if (!page.contains("id=\"rep-count-" + t[0] + "\"")) continue;
            int sum = 0;
            for (String[] s : sections(page)) if (t[1].equals(s[1])) sum += Integer.parseInt(s[2]);
            assertEquals("tile " + t[0], count(page, t[0]), sum);
            int g = page.indexOf("id=\"rep-" + t[1] + "\"");
            if (sum > 0) {
                assertTrue("group " + t[1], g > 0);
                String pill = page.substring(page.indexOf("rep-pill\">", g) + 10, page.indexOf("</span>", g));
                assertTrue(pill, pill.startsWith(String.format("%,d", sum) + " "));
            }
        }
    }

    // --------------------------------------------------------------------------------------------- tests

    public void testEcofogSummaryAndGroups() {
        Fixture x = ecofog();
        String page = x.render();
        assertSummaryIsTheEmails(x, page);
        assertEquals(2, count(page, "notLoaded"));
        assertEquals(0, count(page, "corrected"));
        // 959 read, 945 loaded, 2 listed as not loaded: 12 lines not in any section, said so.
        assertTrue(page, page.contains("12 other lines were read but not loaded and are not listed below"));
        int notLoaded = page.indexOf("id=\"rep-notLoaded\""), notes = page.indexOf("id=\"rep-note\"");
        assertTrue(notLoaded > 0 && notes > notLoaded);
        assertFalse("no empty group", page.contains("id=\"rep-corrected\"") || page.contains("id=\"rep-redFlag\""));
        assertTrue(page, page.contains("Not loaded (fix these)"));
        // The section texts stay (tests and docs read them).
        assertTrue(page.contains("Code not found (not uploaded)"));
        assertTrue(page.contains("Not valid Antweb adm1"));
        assertTrue(page.contains("Genus not in AntCat (not an ant?) (uploaded)"));
        assertTrue(page.contains("Some specimens were flagged as introduced"));
        assertTrue(page.contains("action=introducedSpecimen&amp;groupId=62"));
        // Every line links to the line as uploaded, as on the specimen page.
        assertTrue(page, page.contains("<a href=\"" + APP + "/logMgr.do?action=uploadLog&amp;file=20261029-14%3A02%3A11-specimen62.txt&amp;line=926\" title=\"This line as uploaded\">926</a>"));
        // 7 adm1 names, Kourou's 64 lines: three shown, the rest under "+61 more".
        assertTrue(page, page.contains("<summary>+61 more</summary>"));
        assertTrue(page, page.contains("<summary>64 specimens</summary>"));
        // Checks that found nothing: one collapsed line.
        int sections = sections(page).size();
        assertTrue(page, page.contains("<summary>" + x.mgr.passedHeadings().size() + " other checks found nothing</summary>"));
        assertEquals(x.mgr.attentionByKey().size() + 2, sections + x.mgr.passedHeadings().size());   // + introduced, caste
        assertFalse("the old list of empty headings is gone", page.contains("Passed Tests"));
    }

    public void testCasNightlyLongSectionsCollapse() {
        Fixture x = casNightly();
        String page = x.render();
        assertSummaryIsTheEmails(x, page);
        assertEquals(280, count(page, "notLoaded"));
        assertEquals(3, count(page, "redFlagged"));
        assertTrue(page.contains("id=\"rep-redFlag\""));
        // Duplicates: one row per holder, each with its lines and codes collapsed.
        String dup = sectionHtml(page, MessageMgr.duplicateEntries);
        assertTrue(dup, dup.contains("data-total=\"280\" data-rows=\"4\""));
        assertTrue(dup, dup.contains("Code already in AntWeb, uploaded by UC Davis (Ward lab)"));
        assertTrue(dup, dup.contains("<summary>171 specimens</summary>"));
        assertTrue(dup, dup.contains("<summary>+168 more</summary>"));
        // 46 recognized invalid names: 10 rows, then the rest under "Show all 46 rows".
        String rec = sectionHtml(page, MessageMgr.recognizedInvalidSpecies);
        assertTrue(rec, rec.contains("<details class=\"rep-more\"><summary>Show all 46 rows</summary>"));
        String first = rec.substring(0, rec.indexOf("rep-more"));
        assertEquals(UploadReportPage.SHOWN_ROWS, occurrences(first, "<td class=\"rep-c-file\""));
        assertEquals(46, occurrences(rec, "<td class=\"rep-c-file\""));
        // "old -> new: reason": the old name in its column, the new one and the reason in AntWeb's.
        assertTrue(rec, rec.contains("<td class=\"rep-c-did\" data-label=\"AntWeb\"><a href='" + APP + "/description.do?taxonName=pheidole+fervens'>Pheidole fervens</a>: <span class=\"name-update-reason\">"));
        assertTrue(page.contains("Recognized invalid species. Submission replaced with current valid name from AntCat.org (uploaded)"));
    }

    public void testEveryOtherSection() {
        Fixture x = everyOtherSection();
        String page = x.render();
        assertSummaryIsTheEmails(x, page);
        // The checks this fixture leaves empty (the other fixtures fill them) are the ones that found nothing.
        assertEquals(USED.size(), x.mgr.passedHeadings().size());
        // Values are escaped where they are added; nothing curator-written becomes markup.
        assertFalse(page, page.contains("sample duplicatedRecord <b>"));
        assertTrue(page, page.contains("&lt;b&gt;&amp;"));
        assertTrue(page, page.contains("ZZ Test Group &lt;staging&gt;"));
        assertFalse(page.contains("ZZ Test Group <staging>"));
        for (String key : x.mgr.attentionByKey().keySet()) {
            if (USED.contains(key)) continue;
            assertTrue("section " + key, page.contains("data-key=\"" + key + "\""));
        }
    }

    public void testEmptyRun() {
        Fixture x = emptyRun();
        String page = x.render();
        assertSummaryIsTheEmails(x, page);
        assertEquals(0, sections(page).size());
        assertFalse(page.contains("class=\"rep-group"));
        assertTrue(page, page.contains("<summary>All " + x.mgr.passedHeadings().size() + " checks found nothing</summary>"));
        assertTrue(page.contains(">Nothing in the report needs your attention.</p>"));
        assertFalse(page.contains("rep-unlisted"));
        assertTrue(page.contains("<span id=\"report-counts-rule\" hidden>2026-10-27</span>"));
        assertTrue(page.contains("<h1 class=\"aw-title\">Upload Report</h1>"));
        assertTrue(page.contains("/uploadReport.do?uploadId=2269"));
    }

    /** Facts the page writes itself are escaped. */
    public void testFactsAreEscaped() {
        Fixture x = emptyRun();
        x.facts.uploadedBy = "<script>alert(1)</script>";
        x.facts.title = "a<b";
        x.facts.encoding = "\"x\"";
        String page = x.render();
        assertFalse(page.contains("<script>"));
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(page.contains("<title>a&lt;b</title>"));
    }

    /** Without the uploaded file's name (species list uploads) rows have no Line column. */
    public void testNoLineLinksWithoutTheFile() {
        Fixture x = ecofog();
        x.facts.uploadFileName = null;
        String page = x.render();
        assertFalse(page.contains("action=uploadLog"));
        assertFalse(page.contains("<td class=\"rep-c-line\""));
    }

    // ------------------------------------------------------------------ 2026-10-30 (Phil Ward's upload, UC Davis)

    /** Phil Ward's upload of 2026-10-30: 24,219 lines, 10 columns that are not AntWeb columns, non-ant wasps
     *  (Schlettererius, Typhoctes, Pepsis...), one real "Preferred Subfamily For Genus" ant line, one taxon in two
     *  bioregions. The parse step's decisions are SpecimenUploadParse's (isAnt here: the subfamily is an ant one). */
    static final Set<String> ANT_SUBFAMILIES = new HashSet<>(Arrays.asList("myrmicinae", "formicinae", "ponerinae", "dorylinae"));
    static boolean isAnt(String taxonName) {
        if (taxonName.contains("(formicidae)")) return true;
        for (String sf : ANT_SUBFAMILIES) if (taxonName.startsWith(sf)) return true;
        return false;
    }
    static final String[] IGNORED = {"adm1check", "blank", "count", "ew", "iso_3166", "latdeg", "latmin", "londeg", "lonmin", "ns"};

    public static Fixture wardUpload() {
        Fixture x = new Fixture(41, "UC Davis (Ward lab)", "20261030-10:09:02-specimen41.txt", "specimenUpload");
        UploadReportPage.Facts f = x.facts;
        f.uploadId = 2275;
        f.date = "Fri Oct 30 10:31:40 PDT 2026";
        f.recordCount = 24_219;
        f.parsed = 24_219;
        f.processed = 24_219;
        f.insertedSpecimens = 24_219;
        // { file's subfamily, genus, subfamily of AntWeb's genus row, species }
        String[][] lines = {
            {"schlettereriinae", "schlettererius", "(schlettereriinae)", "cinctipes"},
            {"schlettereriinae", "schlettererius", "(schlettereriinae)", "cinctipes"},
            {"typhoctinae", "typhoctes", "(typhoctinae)", "peculiaris"},
            {"typhoctinae", "typhoctes", "(typhoctinae)", "peculiaris"},
            {"pepsinae", "pepsis", "(pepsinae)", "sp"},
            {"myrmicinae", "aenictus", "dorylinae", "ceylonicus"},          // a real ant: AntCat files Aenictus under Dorylinae
        };
        for (int line = 2; line <= 24_220; line++) {
            x.at(line, String.format("psw%05d", line));
            for (String c : IGNORED) x.mgr.addToMessagesOnce(MessageMgr.unrecognizedColumn, Html.esc(c));
            if (line - 2 < lines.length) {
                String[] l = lines[line - 2];
                if (SpecimenUploadParse.isPreferredSubfamilyReplacement(l[0], l[2], l[1], UploadReportPageTest::isAnt)) {
                    x.mgr.addToMapMessages(MessageMgr.preferredSubfamilyForGenusReplaced, Html.esc(l[0]), taxon(l[2] + l[1], l[2] + " " + l[1]));
                }
                String taxonName = l[2] + l[1] + " " + l[3];
                if (!isAnt(taxonName)) x.mgr.addToMessages(MessageMgr.nameNotInFamilyFormicidae, taxon(taxonName, l[1] + " " + l[3]));
            }
        }
        x.done();
        x.mgr.addToMessages(MessageMgr.multipleBioregionsForNonIntroducedTaxa,
            MessageMgr.multiBioregionLine(taxon("formicinaecamponotus semitestaceus", "Camponotus semitestaceus"), "Nearctic,Neotropical"));
        return x;
    }

    /** A non-ant genus row ('(schlettereriinae)schlettererius') is no "Preferred Subfamily For Genus"; an ant one is. */
    public void testPreferredSubfamilyOnlyForAnts() {
        assertFalse(SpecimenUploadParse.isPreferredSubfamilyReplacement("schlettereriinae", "(schlettereriinae)", "schlettererius", UploadReportPageTest::isAnt));
        assertFalse(SpecimenUploadParse.isPreferredSubfamilyReplacement("ampulicinae", "(ampulicinae)", "ampulex", UploadReportPageTest::isAnt));
        assertTrue(SpecimenUploadParse.isPreferredSubfamilyReplacement("myrmicinae", "dorylinae", "aenictus", UploadReportPageTest::isAnt));
        assertFalse(SpecimenUploadParse.isPreferredSubfamilyReplacement("(formicidae)", "dorylinae", "aenictus", UploadReportPageTest::isAnt));
        assertTrue(SpecimenUploadParse.isAntGenus("(formicidae)", "termes", UploadReportPageTest::isAnt));
        assertFalse(SpecimenUploadParse.isAntGenus("(typhoctinae)", "typhoctes", UploadReportPageTest::isAnt));
    }

    /** A non-ant line is listed once, under "Not ants (not in AntCat)", a note; not corrected, not in the email's count. */
    public void testNonAntListedOnceNotCorrected() {
        Fixture x = wardUpload();
        String page = x.render();
        assertSummaryIsTheEmails(x, page);
        assertEquals(1, x.mgr.getCorrectedCount());                     // the Aenictus line only
        assertEquals(1, count(page, "corrected"));
        assertEquals(1, occurrences(page, MessageMgr.NOT_ANTS_HEADING + " <span"));
        String notAnts = sectionHtml(page, MessageMgr.nameNotInFamilyFormicidae);
        assertTrue(notAnts, notAnts.contains("data-kind=\"" + MessageMgr.NOTE + "\" data-total=\"5\" data-rows=\"3\""));
        assertEquals("Schlettererius named once on the page", 1, occurrences(page, ">schlettererius cinctipes<"));
        String pref = sectionHtml(page, MessageMgr.preferredSubfamilyForGenusReplaced);
        assertTrue(pref, pref.contains("data-total=\"1\""));
        assertFalse(pref, pref.contains("schlettererius") || pref.contains("typhoctes"));
        assertTrue(pref, pref.contains("aenictus"));
        assertFalse(page.contains("data-key=\"" + MessageMgr.groupMorphoGenera + "\""));
        assertFalse(page.contains("Names not in Family Formicidae"));
    }

    /** 10 ignored columns over 24,219 lines count as 10: one row per column, no line numbers, the Notes tile not inflated. */
    public void testIgnoredColumnsCountOnce() {
        Fixture x = wardUpload();
        String page = x.render();
        assertSummaryIsTheEmails(x, page);
        String cols = sectionHtml(page, MessageMgr.unrecognizedColumn);
        assertTrue(cols, cols.contains("data-total=\"10\" data-rows=\"10\""));
        assertTrue(cols, cols.contains(MessageMgr.COLUMNS_IGNORED_HEADING + ": 10</h3>"));
        assertFalse(cols, cols.contains("rep-c-line"));
        assertFalse(cols, cols.contains("action=uploadLog"));
        for (String c : IGNORED) assertTrue(c, cols.contains(">" + c + "</td>"));
        // Notes: 10 columns + 5 non-ant lines + 1 taxon in two bioregions.
        assertEquals(16, count(page, "notes"));
        assertEquals(16, x.mgr.getNoteCount());
        assertTrue(page, page.contains("<span class=\"rep-pill\">16 items</span></h2>"));
        assertFalse(page.contains("Unrecognized Column"));
    }

    /** "Multiple bioregions for non-introduced taxa" lists each taxon and its bioregions. */
    public void testMultipleBioregionsListsNames() {
        Fixture x = wardUpload();
        String page = x.render();
        String mb = sectionHtml(page, MessageMgr.multipleBioregionsForNonIntroducedTaxa);
        assertTrue(mb, mb.contains("data-total=\"1\" data-rows=\"1\""));
        assertTrue(mb, mb.contains("1 taxon</span>"));
        assertTrue(mb, mb.contains(">Camponotus semitestaceus</a>: Nearctic, Neotropical</td>"));
        assertTrue(mb, mb.contains("action=multiBioregionTaxaList&amp;groupId=41"));
    }

    /** One line-number column: a value's leading "line:N" moves to the Line column, linked when the line was recorded. */
    public void testOneLineNumberColumn() {
        Fixture x = ecofog();
        // A row whose line was not recorded (the report's link budget used up): its number goes to the Line column.
        x.mgr.setCurrentLine(0);
        x.mgr.addToMessages(MessageMgr.codeNotFound, "line:1003 code:");
        String page = x.render();
        String sec = sectionHtml(page, MessageMgr.codeNotFound);
        assertTrue(sec, sec.contains("<td class=\"rep-c-line\" data-label=\"Line\">1003</td>"));
        assertTrue(sec, sec.contains("line=926\" title=\"This line as uploaded\">926</a></td>"));
        assertFalse(sec, sec.contains("line:"));
        assertEquals(1, occurrences(sec, "<th class=\"rep-c-line\">"));
    }

    static String sectionHtml(String page, String key) {
        int i = page.indexOf("data-key=\"" + key + "\"");
        assertTrue("no section " + key, i > 0);
        return page.substring(i, page.indexOf("</section>", i));
    }

    /** The same upload as the page looked until 2026-10-29 (UploadDetails.genUploadReport before; preview only). */
    static String oldPage(Fixture x) {
        UploadReportPage.Facts f = x.facts;
        String b = "&nbsp;&nbsp;&nbsp;";
        String s = "<head><title>" + f.title + "</title><meta charset=\"utf-8\"/></head><body>"
            + "<h2>Upload Report" + (x.mgr.getMessages().isEmpty() ? "" : ": <font color=red>Check Your Data Errors</font>") + "</h2>"
            + "<a href = \"" + APP + "\">Home</a> | <a href = \"" + APP + "/curate.do\">Curator Tools</a><br><br>"
            + "<h3>Upload Details:</h3>"
            + b + "<b>Upload ID:</b> <a href='" + APP + "/uploadReport.do?uploadId=" + f.uploadId + "'>" + f.uploadId + "</a>"
            + "<br> " + b + "<b>Operation: " + f.operation + "</b>"
            + "<br>" + b + "<b>This Log File:</b> " + f.logFileHtml
            + "<br>" + b + "<b>Date:</b> " + f.date
            + "<br>" + b + "<b>Encoding:</b> " + f.encoding
            + "<br>" + b + "<b>Antweb version:</b> " + f.version
            + "<br>" + b + "<b>Uploaded File:</b> " + f.uploadedFileHtml
            + "<br>" + b + "<b>Uploaded by:</b> " + f.uploadedBy
            + "<br>" + b + "<b>Group Id:</b> " + f.groupHtml
            + "<br>" + b + "<b>Curator:</b> " + f.curatorHtml
            + "<br>" + b + "<b>Record Count:</b> " + f.recordCount;
        if ("specimenUpload".equals(f.operation)) {
            s += "<br>" + b + "<b>Parsed:</b> " + f.parsed + "<br>" + b + "<b>Processed:</b> " + f.processed
                + "<br>" + b + "<b>Museums:</b> <a href=''>1</a><br>" + b + "<b>Inserted Specimens:</b> " + f.insertedSpecimens
                + "<br>" + b + "<b>Red Flagged Specimens:</b> " + x.mgr.getRedFlagCount()
                + "<br>" + b + "<b>Advanced Search:</b><a href=\"" + APP + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&uploadId=" + f.uploadId + "\"> This Upload</a>*";
        }
        s += "<br>" + b + "<b>Exec Time:</b> " + f.execTime + "\r\r<br><br><h3>Statistics</h3>";
        if (f.insertedSpecies > 0) s += "<br>" + b + "<b>Inserted Species:</b> " + f.insertedSpecies + ".";
        if (f.updatedSpecies > 0) s += "<br>" + b + "<b>Updated Species:</b> " + f.updatedSpecies + ".";
        return s + x.mgr.getMessagesReport() + "</body>";
    }

    /** Writes the preview pages (scratch use): java ... UploadReportPageTest <dir>. */
    public static void main(String[] args) throws Exception {
        java.nio.file.Path dir = java.nio.file.Paths.get(args[0]);
        java.nio.file.Files.createDirectories(dir);
        Map<String, Fixture> all = new java.util.LinkedHashMap<>();
        all.put("ecofog", ecofog());
        all.put("cas-nightly", casNightly());
        all.put("every-section", everyOtherSection());
        all.put("empty-run", emptyRun());
        all.put("ward-2026-10-30", wardUpload());
        for (Map.Entry<String, Fixture> e : all.entrySet()) {
            e.getValue().facts.cssHref = "tools.css";
            String page = e.getValue().render();
            java.nio.file.Files.writeString(dir.resolve(e.getKey() + "-after.html"), page);
            java.nio.file.Files.writeString(dir.resolve(e.getKey() + "-before.html"), oldPage(e.getValue()));
        }
    }
}

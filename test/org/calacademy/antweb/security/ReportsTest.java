package test.org.calacademy.antweb.security;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.util.ListAction;
import org.calacademy.antweb.util.PartsReport;
import org.calacademy.antweb.util.ReportHelp;

/** Admin report pages (2026-10-19): the -D part code rule, the parts report, the explanation boxes. */
public class ReportsTest extends TestCase {

    public void testPartBaseOnlyAtTheEnd() {
        assertEquals("casent0001234", SpecimenDb.partBase("casent0001234-d01"));
        assertEquals("casent0001234", SpecimenDb.partBase("casent0001234-d1"));
        assertEquals("CASENT0001234", SpecimenDb.partBase("CASENT0001234-D02"));
        assertEquals("casent0001234-d01", SpecimenDb.partBase("casent0001234-d01-d02"));
        assertNull("no digits", SpecimenDb.partBase("casent0001234-d"));
        assertNull("not at the end", SpecimenDb.partBase("casent0001234-d01x"));
        assertNull("-d inside the code", SpecimenDb.partBase("casent-dorylus01"));
        assertNull("-d then letters", SpecimenDb.partBase("jtl-dsa01"));
        assertNull("no base", SpecimenDb.partBase("-d01"));
        assertNull(SpecimenDb.partBase("casent0001234"));
        assertNull(SpecimenDb.partBase(null));
        assertEquals("the SQL pattern is the same rule", "-d[0-9]+$", SpecimenDb.PART_SUFFIX_SQL);
    }

    public void testPartsReport() {
        String empty = ListAction.partsReport(new ArrayList<>());
        assertTrue(empty.contains("report-help"));
        assertTrue(empty.contains("None"));
        assertTrue(empty.contains("<b>0 specimens</b>"));

        List<SpecimenDb.PartGroup> groups = new ArrayList<>();
        SpecimenDb.PartGroup g = new SpecimenDb.PartGroup();
        g.base = "casent0001234";
        g.parts.add(new SpecimenDb.PartRow("casent0001234", "myrmicinaepheidole megacephala", 1, "California Academy of Sciences"));
        g.parts.add(new SpecimenDb.PartRow("casent0001234-d01", "myrmicinaepheidole <b>x", 1, "California Academy of Sciences"));
        groups.add(g);
        String page = ListAction.partsReport(groups);
        assertTrue(page.contains("<b>1 specimen</b>"));
        assertEquals(1, count(page, ">casent0001234</a></td><td><a"));
        assertTrue(page.contains("rowspan=2"));
        assertTrue("names escaped", page.contains("myrmicinaepheidole &lt;b&gt;x"));
        assertTrue(page.contains("California Academy of Sciences (1)"));
        assertTrue(page.contains("Download this list (CSV)"));
    }

    /** Three specimens: CAS only, JTL only, and one whose base is CAS and whose part is JTL. */
    private static List<SpecimenDb.PartGroup> sample() {
        List<SpecimenDb.PartGroup> groups = new ArrayList<>();
        SpecimenDb.PartGroup a = new SpecimenDb.PartGroup();
        a.base = "casent0000002";
        a.parts.add(new SpecimenDb.PartRow("casent0000002", "formicinaecamponotus a", 1, "California Academy of Sciences"));
        a.parts.add(new SpecimenDb.PartRow("casent0000002-d01", "formicinaecamponotus b", 1, "California Academy of Sciences"));
        SpecimenDb.PartGroup b = new SpecimenDb.PartGroup();
        b.base = "jtl0000001";
        b.parts.add(new SpecimenDb.PartRow("jtl0000001-d01", "myrmicinaepheidole a", 7, "Utah"));
        SpecimenDb.PartRow r = new SpecimenDb.PartRow("jtl0000001-d02", "myrmicinaepheidole b", 7, "Utah");
        r.originalTaxonName = "myrmicinaepheidole old";
        r.determinedBy = "J. Longino, \"JTL\"";
        r.dateDetermined = "2020-05-01";
        r.museum = "JTLC";
        b.parts.add(r);
        SpecimenDb.PartGroup c = new SpecimenDb.PartGroup();
        c.base = "casent0000001";
        c.parts.add(new SpecimenDb.PartRow("casent0000001", "dorylinaedorylus a", 1, "California Academy of Sciences"));
        c.parts.add(new SpecimenDb.PartRow("casent0000001-d01", "=dorylinaedorylus b", 7, "Utah"));
        groups.add(a); groups.add(b); groups.add(c);
        return groups;
    }

    public void testGroupingAndFilter() {
        List<PartsReport.GroupList> lists = PartsReport.byGroup(sample());
        assertEquals(2, lists.size());
        assertEquals("sorted by name", "California Academy of Sciences", lists.get(0).name);
        assertEquals(2, lists.get(0).specimens.size());
        assertEquals("by base code", "casent0000001", lists.get(0).specimens.get(0).base);
        assertEquals("Utah", lists.get(1).name);
        assertEquals("a mixed specimen is under both groups", 2, lists.get(1).specimens.size());
        assertEquals("casent0000001", lists.get(1).specimens.get(0).base);
        assertEquals("jtl0000001", lists.get(1).specimens.get(1).base);

        assertEquals(1, PartsReport.select(lists, 7).size());
        assertEquals(0, PartsReport.select(lists, 99).size());
        assertEquals(2, PartsReport.select(lists, null).size());
        assertNull(PartsReport.parseGroupId("x"));
        assertNull(PartsReport.parseGroupId(""));
        assertEquals(Integer.valueOf(7), PartsReport.parseGroupId("7"));

        String page = PartsReport.html(sample(), 7, "https://www.antweb.org");
        assertTrue(page.contains("Utah (2)"));
        assertTrue(page.contains("California Academy of Sciences (2)"));
        assertTrue(page.contains("All groups (3)"));
        assertTrue(page.contains("value=\"7\" selected"));
        assertEquals("only the Utah table", 1, count(page, "<table"));
        assertEquals("the CAS base row is marked", 1, count(page, "class=\"other-group\""));
        assertTrue(page.contains("groupId=7&amp;format=csv"));
        assertTrue(page.contains("myrmicinaepheidole old"));
        assertTrue(page.contains("2020-05-01"));
        assertTrue(page.contains("each curator can only fix the specimens their group uploaded"));
        assertTrue(PartsReport.html(sample(), 99, "").contains("No specimens for this group."));
        String all = PartsReport.html(sample(), null, "");
        assertEquals(2, count(all, "<table"));
    }

    public void testCsv() {
        String csv = PartsReport.csv(sample(), 7, "https://www.antweb.org");
        String[] lines = csv.split("\r\n");
        assertEquals(PartsReport.CSV_HEADER, lines[0]);
        assertEquals(5, lines.length);
        assertTrue(lines[1].startsWith("Utah,casent0000001,casent0000001,dorylinaedorylus a,"));
        assertTrue(lines[1].contains(",California Academy of Sciences,no,"));
        assertTrue("formula prefix", lines[2].contains(",'=dorylinaedorylus b,"));
        assertTrue(lines[2].contains(",Utah,yes,"));
        assertTrue(lines[4].contains("\"J. Longino, \"\"JTL\"\"\",2020-05-01,myrmicinaepheidole old,Utah,yes,JTLC,"));
        assertTrue(lines[4].endsWith("https://www.antweb.org/specimen.do?code=jtl0000001-d02"));
        assertEquals("all groups: the mixed specimen twice", 1 + 4 + 4, PartsReport.csv(sample(), null, "").split("\r\n").length);
        assertEquals("parts-different-names-group-7.csv", PartsReport.csvFileName(7));
    }

    public void testExplanationBoxes() {
        for (String box : new String[] {ReportHelp.INTEGRITY, ReportHelp.PARTS, ReportHelp.HOMONYMS, ReportHelp.WORLDANTS_UPLOADS}) {
            assertTrue(box.contains("What this shows:"));
            assertTrue(box.contains("Why it matters:"));
            assertTrue(box.contains("What to do with a hit:"));
        }
    }

    private static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }
}

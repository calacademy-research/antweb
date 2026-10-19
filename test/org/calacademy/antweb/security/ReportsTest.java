package test.org.calacademy.antweb.security;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.util.ListAction;
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
        g.parts.add(new String[] {"casent0001234", "myrmicinaepheidole megacephala"});
        g.parts.add(new String[] {"casent0001234-d01", "myrmicinaepheidole <b>x"});
        groups.add(g);
        String page = ListAction.partsReport(groups);
        assertTrue(page.contains("<b>1 specimen</b>"));
        assertEquals(1, count(page, ">casent0001234</a></td><td><a"));
        assertTrue(page.contains("rowspan=2"));
        assertTrue("names escaped", page.contains("myrmicinaepheidole &lt;b&gt;x"));
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

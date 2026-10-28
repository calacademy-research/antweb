package test.org.calacademy.antweb.upload;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Vector;

import junit.framework.TestCase;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.upload.CurrentValidNameRule;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.upload.SpecimenUploadProcess;
import org.calacademy.antweb.upload.UploadFile;
import org.calacademy.antweb.upload.UploadUtil;

/**
 * Upload decisions of Brian Fisher, 2026-10-28 (no database):
 *  8. AntCat's current valid name has no taxon in AntWeb yet: the specimen stays under its uploaded name and the
 *     report lists it ("Current valid name in AntCat not yet in AntWeb: kept under the uploaded name").
 *  9. A line with a genus but no species, or a subfamily but no genus or species, is filed as indet and listed
 *     ("Identified only to genus: filed as indet", "Identified only to subfamily: filed as (Subfamily) indet").
 * 10. Upload files are UTF-8 only: a file that is not is refused, naming its first line that is not.
 * One form for identified only to genus: species "indet", "Indet", "indet.", "undet" is filed as "(indet)", the same
 * name as a blank species ("Pheidole (indet)"), and listed in the same report section.
 */
public class UploadDecisions1028Test extends TestCase {

    // ------------------------------------------------------------ 8

    static CurrentValidNameRule.Result rule(String name) throws SQLException {
        return CurrentValidNameRule.decide(name, new CurrentValidNameRule.Lookup() {
            public CurrentValidNameRule.TaxonRow getTaxon(String n) {
                if ("ponerinaehypoponera lost".equals(n)) return new CurrentValidNameRule.TaxonRow(Status.SYNONYM, "ponerinaehypoponera missing");
                return null;
            }
            public String getCurrentValidTaxonName(String n) { return null; }
            public boolean isHomonym(String n) { return false; }
        });
    }

    public void testMissingValidNameKeepsTheUploadedName() throws Exception {
        CurrentValidNameRule.Result r = rule("ponerinaehypoponera lost");
        assertEquals(CurrentValidNameRule.Kind.TARGET_MISSING, r.kind);
        assertEquals("ponerinaehypoponera lost", r.taxonName);
        assertEquals(Status.SYNONYM, r.status);
        assertNull(r.originalTaxonName);
        assertEquals("ponerinaehypoponera missing", r.missingCurrentValidName);
    }

    public void testMissingValidNameReportLine() {
        String line = SpecimenUploadProcess.currentValidNameNotInAntwebLine("ponerinaehypoponera lost", "ponerinaehypoponera missing");
        assertTrue(line, line.contains("lost"));
        assertTrue(line, line.contains(" -> "));
        assertTrue(line, line.contains("missing"));
        assertTrue(line, line.endsWith("(not in AntWeb yet)"));
        String heading = MessageMgr.getMessageDisplay(MessageMgr.currentValidNameNotInAntweb);
        assertTrue(heading, heading.contains("Current valid name in AntCat not yet in AntWeb: kept under the uploaded name"));
        assertTrue(heading, heading.contains("(uploaded)"));
    }

    // ------------------------------------------------------------ 9

    public void testIdentifiedOnlyTo() {
        assertEquals(SpecimenUploadParse.GENUS, SpecimenUploadParse.identifiedOnlyTo("myrmicinae", "pheidole", null));
        assertEquals(SpecimenUploadParse.GENUS, SpecimenUploadParse.identifiedOnlyTo(null, "pheidole", ""));
        assertEquals(SpecimenUploadParse.SUBFAMILY, SpecimenUploadParse.identifiedOnlyTo("myrmicinae", null, null));
        assertEquals(SpecimenUploadParse.SUBFAMILY, SpecimenUploadParse.identifiedOnlyTo("myrmicinae", "", " "));
        assertEquals(SpecimenUploadParse.SUBFAMILY, SpecimenUploadParse.identifiedOnlyTo("myrmicinae", "(indet)", null));
        assertNull("a species was given", SpecimenUploadParse.identifiedOnlyTo("myrmicinae", "pheidole", "megacephala"));
        assertNull("no subfamily either", SpecimenUploadParse.identifiedOnlyTo(null, null, null));
        assertNull("no subfamily either", SpecimenUploadParse.identifiedOnlyTo("(formicidae)", "", ""));
        assertNull(SpecimenUploadParse.identifiedOnlyTo("indet", "", ""));
    }

    public void testIndetEpithetIsTheOneForm() {
        for (String s : new String[] {"indet", "Indet", "INDET", "indet.", "Indet.", " indet ", "undet", "Undet.", "(indet)", "(Indet)."}) {
            assertTrue(s, UploadUtil.isIndetEpithet(s));
            assertEquals(s, "(indet)", UploadUtil.normalizeIndetEpithet(s));
        }
        for (String s : new String[] {"megacephala", "indeterminata", "indet1", "sp.", "sp", "", "indet x", "(indet"}) {
            assertFalse(s, UploadUtil.isIndetEpithet(s));
            assertEquals(s, s, UploadUtil.normalizeIndetEpithet(s));
        }
        assertFalse(UploadUtil.isIndetEpithet(null));
        assertNull(UploadUtil.normalizeIndetEpithet(null));
        // the name built from it is the blank-species name
        java.util.Hashtable<String, Object> typed = new java.util.Hashtable<>();
        typed.put("subfamily", "myrmicinae");
        typed.put("genus", "pheidole");
        typed.put("species", UploadUtil.normalizeIndetEpithet("indet."));
        assertEquals("myrmicinaepheidole (indet)", UploadUtil.makeName(typed));
        assertEquals(Status.INDETERMINED, decideStatus(UploadUtil.makeName(typed)));
    }

    static String decideStatus(String name) {
        try {
            return CurrentValidNameRule.decide(name, null).status;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void testIndetEpithetIsIdentifiedOnlyToGenus() {
        for (String s : new String[] {"indet", "Indet", "indet.", "undet", "(indet)", "sp."}) {
            assertEquals(s, SpecimenUploadParse.GENUS, SpecimenUploadParse.identifiedOnlyTo("myrmicinae", "pheidole", s));
            assertEquals(s, SpecimenUploadParse.SUBFAMILY, SpecimenUploadParse.identifiedOnlyTo("myrmicinae", "", s));
        }
        assertNull(SpecimenUploadParse.identifiedOnlyTo("myrmicinae", "pheidole", "indeterminata"));
    }

    public void testIdentifiedOnlyToReportSections() {
        String genus = MessageMgr.getMessageDisplay(MessageMgr.identifiedOnlyToGenus);
        assertTrue(genus, genus.contains("Identified only to genus: filed as indet"));
        assertTrue(genus, genus.contains("(uploaded)"));
        assertFalse(genus, genus.contains("not uploaded"));
        String subfamily = MessageMgr.getMessageDisplay(MessageMgr.identifiedOnlyToSubfamily);
        assertTrue(subfamily, subfamily.contains("Identified only to subfamily: filed as (Subfamily) indet"));
        assertTrue(subfamily, subfamily.contains("(uploaded)"));

        // a section with a count, one line per name
        MessageMgr mgr = new MessageMgr();
        mgr.addToMessages(MessageMgr.identifiedOnlyToGenus, "Pheidole (indet)");
        mgr.addToMessages(MessageMgr.identifiedOnlyToGenus, "Pheidole (indet)");
        mgr.addToMessages(MessageMgr.identifiedOnlyToGenus, "Camponotus (indet)");
        Group group = new Group();
        group.setName("test");
        mgr.compileMessages(group);
        String section = null;
        Vector<String> messages = mgr.getMessages();
        for (String m : messages) if (m.contains("Identified only to genus")) section = m;
        assertNotNull(messages.toString(), section);
        assertTrue(section, section.contains("Identified only to genus: filed as indet <font color=green>(uploaded)</font></b> (total:3)"));
        for (String m : messages) assertFalse(m, m.contains("Empty String Species") || m.contains("Empty String Genus"));
    }

    // ------------------------------------------------------------ 10

    static int bad(byte[] bytes) throws Exception {
        return UploadFile.firstNonUtf8Line(new ByteArrayInputStream(bytes));
    }

    public void testUtf8FileIsAccepted() throws Exception {
        String text = "﻿SpecimenCode\tCountry\ncasent0000001\tMadagascar\ncasent0000002\tCôte d'Ivoire\r\ncasent0000003\tRegión Autónoma";
        assertEquals(0, bad(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, bad(new byte[0]));
    }

    public void testFileNotUtf8NamesItsFirstBadLine() throws Exception {
        String text = "SpecimenCode\tCountry\ncasent0000001\tMadagascar\ncasent0000002\tCôte d'Ivoire\ncasent0000003\tRéunion\n";
        assertEquals("Latin-1 (Excel's plain CSV on some systems)", 3, bad(text.getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("last line without a line end", 2, bad("a\nbé".getBytes(StandardCharsets.ISO_8859_1)));
        String m = UploadFile.notUtf8Message(3);
        assertTrue(m, m.contains("line 3"));
        assertTrue(m, m.contains("Files must be UTF-8 (Excel: Save As, CSV UTF-8)"));
    }
}

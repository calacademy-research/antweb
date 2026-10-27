package test.org.calacademy.antweb.audit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.HashSet;

import junit.framework.TestCase;

import org.calacademy.antweb.CommonNames;
import org.calacademy.antweb.DescEdit;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Rank;
import org.calacademy.antweb.SpecimenXML;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.A;
import org.calacademy.antweb.util.AsciiUtils;
import org.calacademy.antweb.util.CompareUtil;
import org.calacademy.antweb.util.QueryStats;
import org.calacademy.antweb.util.SpecimenListAction;

/**
 * Small fixes in the core and util classes (2026-10-27), checked without a server or a database.
 * Runs in CI inside the antweb image (.github/workflows/tests.yaml).
 */
public class CoreUtilFixesTest extends TestCase {

    /** Specimen XML (from uploads) is parsed without a DOCTYPE: no entity can read a file. */
    public void testSpecimenXmlRefusesDoctype() throws Exception {
        File secret = File.createTempFile("aw-secret", ".txt");
        try {
            Files.writeString(secret.toPath(), "SECRET-12345");
            String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"" + secret.toURI() + "\">]>"
                + "<features><notes>&e;</notes></features>";
            Hashtable h = new SpecimenXML().getHashtable(xml);
            assertTrue(String.valueOf(h), h == null || !String.valueOf(h).contains("SECRET-12345"));
            assertNotNull(new SpecimenXML().isInvalid(xml));

            Hashtable ok = new SpecimenXML().getHashtable("<features><notes>under bark</notes></features>");
            assertEquals("under bark", ok.get("notes"));
        } finally {
            secret.delete();
        }
    }

    public void testCommonNames() {
        for (String value : CommonNames.getNameMap().values()) assertEquals(value, value.trim(), value);
        assertEquals("myrmicinaepheidole megacephala", CommonNames.get("Big-headed ant"));
        assertTrue(CommonNames.getNames().contains("Cornfield ant"));
        assertFalse(CommonNames.getNames().contains("Cornfield abnt"));
    }

    public void testCommaFormatLong() {
        assertEquals("3,000,000,000", Formatter.commaFormat(3_000_000_000L));
        assertEquals("3,000,000,000", A.commaFormat(3_000_000_000L));
        assertEquals("1,234", A.commaFormat("1234"));
    }

    public void testComparatorsAreConsistent() {
        String[] strings = {null, "", "a", "A", "b"};
        for (String x : strings) for (String y : strings) {
            assertEquals(x + "/" + y, Integer.signum(CompareUtil.compareString(x, y)), -Integer.signum(CompareUtil.compareString(y, x)));
            assertEquals(x + "/" + y, Integer.signum(CompareUtil.compareStringDesc(x, y)), -Integer.signum(CompareUtil.compareStringDesc(y, x)));
        }
        String[] ints = {null, "", "5", "12", "x"};
        for (String x : ints) for (String y : ints)
            assertEquals(x + "/" + y, Integer.signum(CompareUtil.compareIntString(x, y)), -Integer.signum(CompareUtil.compareIntString(y, x)));
        assertTrue(CompareUtil.compareIntString("95", "1200") < 0);
        assertEquals(0, CompareUtil.compareIntNoZero(0, 0));
        assertEquals(0, CompareUtil.compareFloat(0f, 0f));
        assertEquals(0, CompareUtil.compareDate(null, null));
        java.util.Date d = new java.util.Date();
        assertEquals(0, CompareUtil.compareDate(d, new java.util.Date(d.getTime())));
    }

    public void testHabitatValueAndCount() {
        String[] vc = Taxon.splitValueCount("Forest: primary:2");
        assertEquals("Forest: primary", vc[0]);
        assertEquals("2", vc[1]);
        assertEquals("leaf litter", Taxon.splitValueCount("leaf litter:14")[0]);
    }

    public void testRanks() {
        assertFalse(Rank.isSubspeciesOrBelow(Rank.GENUS));
        assertFalse(Rank.isSubspeciesOrBelow(Rank.SPECIES));
        assertTrue(Rank.isSubspeciesOrBelow(Rank.SUBSPECIES));
        assertEquals("Families", Rank.getPluralRank(Rank.FAMILY));
    }

    public void testStatusList() {
        java.util.List<String> all = Status.getAllStatuses();
        assertEquals(all.size(), new HashSet<>(all).size());
        assertTrue(all.contains(Status.OBSOLETE_COMBINATION));
        assertTrue(all.contains(Status.OBSOLETE_CLASSIFICATION));
    }

    public void testSmallHelpers() {
        assertTrue(AsciiUtils.isNonAscii("José"));
        assertFalse(AsciiUtils.isNonAscii("Jose"));
        assertEquals("", DescEdit.getPrettyTitle(""));
        assertEquals("Taxonomic Notes", DescEdit.getPrettyTitle("taxonomicnotes"));

        QueryStats stats = new QueryStats();
        stats.count(5);
        stats.count(10);
        stats.count(7);
        assertTrue(stats.log(), stats.log().contains("minMillis:5 "));

        String t1 = SpecimenListAction.randomToken();
        assertTrue(t1, t1.matches("[0-9a-f]{32}"));
        assertFalse(t1.equals(SpecimenListAction.randomToken()));
    }

    /** Deleting a folder removes a symbolic link in it, never the files the link points to. */
    public void testDeleteDirectoryDoesNotFollowLinks() throws Exception {
        Path target = Files.createTempDirectory("aw-target");
        Path keep = Files.writeString(target.resolve("keep.txt"), "x");
        Path dir = Files.createTempDirectory("aw-delete");
        Files.createDirectory(dir.resolve("sub"));
        Files.createSymbolicLink(dir.resolve("sub").resolve("link"), target);
        try {
            Utility.deleteDirectory(dir.toFile());
            assertFalse(Files.exists(dir));
            assertTrue(Files.exists(keep));
        } finally {
            Files.deleteIfExists(keep);
            Files.deleteIfExists(target);
        }
    }
}

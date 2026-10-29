package test.org.calacademy.antweb.util;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.sort.SortTaxaByLifeStage;
import org.calacademy.antweb.sort.SortTaxaBySpecies;
import org.calacademy.antweb.sort.SortTaxaByStatus;
import org.calacademy.antweb.upload.DeleteImagesAction;
import org.calacademy.antweb.util.AntwebSystem;
import org.calacademy.antweb.util.DateUtil;
import org.calacademy.antweb.util.ProjectMgr;
import org.calacademy.antweb.util.StatSet;

/**
 * Fixes of 2026-10-29 that need no database: two-digit collection years are never in the future, the
 * geolocale-lists Admin Alert reads the query's cell, the busy check counts working connections, Recent Images
 * deletes the image a checkbox names, moved projects never redirect to the same URL, sorts with missing values
 * keep the comparator contract, incertae sedis names keep the genus's first letter, the nightly count updates
 * quote names, the "type" status set is not offered to visitors, line counts are read in Java, directories are
 * made under the path (not at the root), and statistics rows escape place names.
 * Runs in CI (.github/workflows/tests.yaml).
 */
public class AuditTjFixesTest extends TestCase {

    private static Object call(Class<?> c, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = c.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static int year(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c.get(Calendar.YEAR);
    }

    public void testTwoDigitYearsAreNeverInTheFuture() throws Exception {
        int thisYear = java.time.Year.now().getValue();
        int yy = (thisYear + 1) % 100;   // next year's two digits: must be read as last century
        assertEquals(1900 + yy, call(DateUtil.class, "fullYear", new Class<?>[] {int.class}, yy));
        assertEquals(2003, call(DateUtil.class, "fullYear", new Class<?>[] {int.class}, 3));
        assertEquals(1966, call(DateUtil.class, "fullYear", new Class<?>[] {int.class}, 66));
        for (String s : new String[] {"3/4/45", "7/45", "21-Jul-45", "4/6/66"}) {
            Date d = DateUtil.constructDate(s);
            assertNotNull(s, d);
            assertFalse(s + " " + d, d.after(new Date()));
            assertTrue(s + " " + d, year(d) < 2000);
        }
    }

    public void testGeolocaleListsAlertReadsTheCell() throws Exception {
        Class<?>[] t = {String.class};
        Class<?> qm = Class.forName("org.calacademy.antweb.util.QueryManager");
        // The header line may quote the description; only the cell decides.
        assertEquals(false, call(qm, "geolocaleTaxaMissing", t, "<th>Result \"records missing!\"</th>\t\n0\n"));
        assertEquals(true, call(qm, "geolocaleTaxaMissing", t, "<th>Result</th>\t\nrecords missing!\n"));
        assertEquals(false, call(qm, "geolocaleTaxaMissing", t, (Object) null));
    }

    public void testBusyCheckCountsWorkingConnectionsOnly() throws Exception {
        List<String> list = new ArrayList<>();
        list.add("| Id     | User   | Host            | db   | Command | Time | State                | Info   \r\n");
        list.add("--------------------------------------------------------------------------------------------\r\n");
        for (int i = 0; i < 150; i++) list.add(i + " | antweb | 10.0.0.1:1 | ant | Sleep | 5 |  | null\r\n");
        for (int i = 0; i < 5; i++) list.add("x" + i + " | antweb | 10.0.0.1:1 | ant | Query | 1 | executing | select 1\r\n");
        Class<?> c = Class.forName("org.calacademy.antweb.util.DBStatus");
        assertEquals(5, call(c, "activeProcessCount", new Class<?>[] {List.class}, list));
    }

    public void testDeleteImagesReadsTheImageNotARowNumber() throws Exception {
        Class<?>[] t = {String.class};
        String value = DeleteImagesAction.chosenValue("casent0005428", "h", "1");
        String[] parts = (String[]) call(DeleteImagesAction.class, "parseChosenImage", t, value);
        assertEquals(Arrays.asList("casent0005428", "h", "1"), Arrays.asList(parts));
        assertNull(call(DeleteImagesAction.class, "parseChosenImage", t, "3"));          // an old page's row index
        assertNull(call(DeleteImagesAction.class, "parseChosenImage", t, "a|h|x1"));     // not a number
        assertNull(call(DeleteImagesAction.class, "parseChosenImage", t, "a||1"));
    }

    public void testMovedProjectNeverRedirectsToItself() {
        assertNull(ProjectMgr.movedBioregionTarget("/browse.do?projectName=africanants&rank=genus"));
        assertNull(ProjectMgr.movedBioregionTarget("/search.do?q=malagasyantsx"));
        assertEquals("/browse.do?rank=genus&bioregionName=Afrotropical",
            ProjectMgr.movedBioregionTarget("/browse.do?rank=genus&project=africanants"));
    }

    private static Taxon taxon(String status) {
        Taxon t = new Taxon();
        t.setStatus(status);
        return t;
    }

    public void testSortsWithMissingValuesKeepTheContract() {
        Random r = new Random(7);
        for (int seed = 0; seed < 20; seed++) {
            List<Taxon> list = new ArrayList<>();
            List<Specimen> specimens = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                list.add(taxon(r.nextInt(5) == 0 ? null : (r.nextBoolean() ? "valid" : "synonym")));
                Specimen s = new Specimen();
                s.setLifeStage(r.nextInt(4) == 0 ? null : (r.nextBoolean() ? "Worker" : "queen"));
                specimens.add(s);
            }
            Collections.shuffle(list, new Random(seed));
            list.sort(new SortTaxaByStatus());
            assertEquals("valid", list.get(0).getStatus());
            assertNull(list.get(list.size() - 1).getStatus());
            list.sort(new SortTaxaBySpecies());   // no taxon sets at all: no exception
            List<Taxon> asTaxa = new ArrayList<>(specimens);
            Collections.shuffle(asTaxa, new Random(seed));
            asTaxa.sort(new SortTaxaByLifeStage());
            assertNull(((Specimen) asTaxa.get(asTaxa.size() - 1)).getLifeStage());
            assertEquals("queen", ((Specimen) asTaxa.get(0)).getLifeStage());
        }
        assertTrue(new SortTaxaByLifeStage().compare(taxon("valid"), taxon("valid")) == 0);   // not specimens
    }

    public void testIncertaeSedisKeepsTheGenusFirstLetter() {
        assertEquals("Incertae Sedis Noonilla", Taxon.getLongPrettyTaxonName("incertae_sedisnoonilla"));
        String link = Taxon.displaySubfamilyGenusLinkToGenus("incertae_sedisnoonilla");
        assertTrue(link, link.contains("genus=noonilla"));
        assertTrue(link, link.contains("/description.do?"));
    }

    public void testCountUpdatesQuoteTheName() {
        Country c = new Country();
        c.setId(5);
        assertTrue(c.getUpdateCountSQL("x'y\\", "species_count", 3).endsWith("taxon_name = 'x''y\\\\'"));
        assertTrue(c.getUpdateImageCountSQL("x'y", 3).endsWith("taxon_name = 'x''y'"));
    }

    public void testTypeStatusSetNotOfferedToVisitors() {
        assertFalse(StatusSet.getStatusSets().contains(StatusSet.TYPE));
        assertTrue(StatusSet.getAdminStatusSets().contains(StatusSet.TYPE));
    }

    public void testLinesAreCountedInJava() throws Exception {
        File f = File.createTempFile("tjlines", ".txt");
        try {
            Files.write(f.toPath(), "a\tb\nc\n\nd\n".getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 50; i++) assertEquals(4, AntwebSystem.countLines(f.getPath()));
            assertEquals(0, AntwebSystem.countLines(f.getPath() + ".missing"));
        } finally {
            f.delete();
        }
    }

    public void testDirectoriesAreMadeUnderThePath() throws Exception {
        File root = Files.createTempDirectory("tjdirs").toFile();
        File file = new File(root, "a/b/c/report.jsp");
        assertTrue(Utility.makeDirTree(file.getPath()));
        assertTrue(new File(root, "a/b/c").isDirectory());
        assertFalse(file.exists());                                   // the last name is never made a directory
        assertTrue(Utility.makeDirTree(root.getPath() + "/d/e/"));    // a path ending with "/" is a directory
        assertTrue(new File(root, "d/e").isDirectory());
        assertTrue(Utility.makeDirTree(root.getPath() + "/f/noExtension"));
        assertTrue(new File(root, "f").isDirectory());
        assertFalse(new File(root, "f/noExtension").exists());
    }

    public void testCasteAbbreviationsAreWholeLetters() {
        assertEquals("queen", org.calacademy.antweb.Caste.getCasteValues("new queen")[0]);
        assertEquals("male", org.calacademy.antweb.Caste.getCasteValues("yellow male")[0]);
        for (String w : new String[] {"w", "1w", "12w", "worker", "2 workers", "dw"}) {
            assertEquals(w, "worker", org.calacademy.antweb.Caste.getCasteValues(w)[0]);
        }
        assertEquals("male", org.calacademy.antweb.Caste.getCasteValues("2m")[0]);
        assertEquals("queen", org.calacademy.antweb.Caste.getCasteValues("dealate queen")[0]);
    }

    public void testStatisticsRowEscapesThePlaceName() {
        StatSet s = new StatSet("A<b>&C");
        String row = s.toString();
        assertTrue(row, row.contains("A&lt;b&gt;&amp;C"));
        assertFalse(row, row.contains("<b>"));
    }
}

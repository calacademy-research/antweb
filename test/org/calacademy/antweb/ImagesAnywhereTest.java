package test.org.calacademy.antweb;

import java.util.*;

import junit.framework.TestCase;

import org.calacademy.antweb.ImagedCastes;
import org.calacademy.antweb.ImagesAnywhere;
import org.calacademy.antweb.home.StatisticsDb;

/**
 * Images from anywhere (Brian Fisher, 2026-10-27; docs/numbers.md rows 44 and 120, docs/flows/taxon-pages.md).
 * No database.
 */
public class ImagesAnywhereTest extends TestCase {

    private static final java.util.function.Function<String, String> CODE = s -> s;

    public void testPlaceSpecimensFirstThenRepresentativesThenTheRest() {
        // The list as sorted by caste: the order within each group is kept.
        List<String> byCaste = Arrays.asList("png1", "idn1", "MYS1", "png2", "idn2", "rep1");
        Set<String> place = new HashSet<>(Arrays.asList("IDN1", "idn2"));
        List<String> reps = Arrays.asList("rep1", "mys1");
        assertEquals(Arrays.asList("idn1", "idn2", "MYS1", "rep1", "png1", "png2"),
            ImagesAnywhere.placeFirst(byCaste, CODE, place, reps));
    }

    public void testRepresentativeFirstWhenThePlaceHasNone() {
        List<String> byCaste = Arrays.asList("casent0882105", "casent0270569", "casent0650191");
        assertEquals(Arrays.asList("casent0270569", "casent0882105", "casent0650191"),
            ImagesAnywhere.placeFirst(byCaste, CODE, new HashSet<>(), Collections.singletonList("CASENT0270569")));
    }

    public void testNoPlaceCodesKeepsTheOrder() {
        List<String> list = Arrays.asList("b", "a");
        assertEquals(list, ImagesAnywhere.placeFirst(list, CODE, null, Collections.singletonList("a")));
        assertTrue(ImagesAnywhere.isFromPlace("CASENT1", new HashSet<>(Collections.singletonList("casent1"))));
        assertFalse(ImagesAnywhere.isFromPlace("casent1", null));
    }

    /** Brian Fisher 2026-10-28: one number, the images of the name in AntWeb ("153 Images"); "153 Images (0 from
     *  Comoros)" wrapped to two lines and the number script blanked the 0 ("( from Comoros)"). */
    public void testImagesColumnShowsTheAntWebNumberOnly() {
        assertEquals("<span class='numbers'>153</span> Images", ImagesAnywhere.imagesColumn(153, 0, "from Comoros"));
        assertEquals("<span class='numbers'>12</span> Images", ImagesAnywhere.imagesColumn(12, 3, "in CASC"));
        for (String phrase : new String[] {"from Comoros", "in CASC", null}) {
            for (int[] n : new int[][] {{153, 0}, {12, 3}, {5, 5}, {-1, 7}, {0, 0}}) {
                String cell = ImagesAnywhere.imagesColumn(n[0], n[1], phrase);
                assertFalse(cell, cell.contains("from ") || cell.contains(" in ") || cell.contains("("));
                assertEquals(cell, cell.startsWith("No ") ? 0 : 1, cell.split("<span", -1).length - 1);
            }
        }
    }

    public void testImagesColumnFallsBackToThePlaceNumber() {
        assertEquals("<span class='numbers'>5</span> Images", ImagesAnywhere.imagesColumn(5, 5, "from Comoros"));
        // a project (no place phrase): the larger of the two
        assertEquals("<span class='numbers'>9</span> Images", ImagesAnywhere.imagesColumn(9, 5, null));
        // the AntWeb-wide number not known (-1) or counted on an earlier night (lower): the place's number
        assertEquals("<span class='numbers'>5</span> Images", ImagesAnywhere.imagesColumn(-1, 5, "from Comoros"));
        assertEquals("<span class='numbers'>5</span> Images", ImagesAnywhere.imagesColumn(3, 5, "from Comoros"));
    }

    public void testNoImagesOnlyWhenThereIsNoImageInAntWeb() {
        assertEquals("No Images", ImagesAnywhere.imagesColumn(0, 0, "from Comoros"));
        assertEquals("No Images", ImagesAnywhere.imagesColumn(-1, 0, "from Comoros"));
        assertFalse(ImagesAnywhere.imagesColumn(1, 0, "from Comoros").startsWith("No "));
    }

    private static org.calacademy.antweb.TaxonSet row(String rank, int global, int place) {
        org.calacademy.antweb.geolocale.GeolocaleTaxon ts = new org.calacademy.antweb.geolocale.GeolocaleTaxon();
        ts.setRank(rank);
        ts.setGlobalImageCount(global);
        ts.setImageCount(place);
        return ts;
    }

    /** PR #291 staging run 2 counted species rows live from the image table (one count per row): list, taxon and
     *  browse pages came back cut off. Every rank reads the allantwebants row again (one key lookup per row), which
     *  nightly set 4 now counts (ProjTaxonCountDb.imageCrawlProjects). */
    public void testEveryRankReadsTheAllAntwebRow() {
        for (String rank : new String[] {"species", "subspecies", "genus", "subfamily"}) {
            String col = row(rank, -1, 0).globalImageCountColumn("ponerinaeleptogenys pavesii");
            assertTrue(col, col.contains("from proj_taxon g where g.project_name = 'allantwebants'"));
            assertTrue(col, col.contains("g.taxon_name = 'ponerinaeleptogenys pavesii' limit 1)"));
            assertTrue(col, col.endsWith(" global_image_count"));
            assertFalse(col, col.contains("join image"));
        }
    }

    public void testImagesColumnOfARow() {
        assertEquals("<span class='numbers'>20</span> Images", ImagesAnywhere.imagesColumn(row("species", 20, 0), "from Comoros"));
        assertEquals("<span class='numbers'>137</span> Images", ImagesAnywhere.imagesColumn(row("species", 137, 8), "from Anguilla"));
        // the allantwebants row not counted yet (0) or not known (-1): the place's number
        assertEquals("<span class='numbers'>8</span> Images", ImagesAnywhere.imagesColumn(row("species", 0, 8), "from Anguilla"));
        assertEquals("<span class='numbers'>7</span> Images", ImagesAnywhere.imagesColumn(row("genus", -1, 7), "from Comoros"));
        // a project's list (no place phrase)
        assertEquals("<span class='numbers'>12</span> Images", ImagesAnywhere.imagesColumn(row("species", 12, 12), null));
        assertEquals("No Images", ImagesAnywhere.imagesColumn(row("species", 0, 0), "from Comoros"));
        assertEquals("No Images", ImagesAnywhere.imagesColumn((org.calacademy.antweb.TaxonSet) null, "from Comoros"));
        // sorting by Images follows the number shown
        assertEquals(137, ImagesAnywhere.shownImages(row("species", 137, 8)));
        assertEquals(7, ImagesAnywhere.shownImages(row("genus", -1, 7)));
        assertEquals(0, ImagesAnywhere.shownImages(null));
    }

    /** readGlobalImageCount never throws: a query without the column leaves -1, a NULL (no allantwebants row) too. */
    public void testReadGlobalImageCountNeverThrows() {
        org.calacademy.antweb.geolocale.GeolocaleTaxon ts = new org.calacademy.antweb.geolocale.GeolocaleTaxon() {
            { readGlobalImageCount(fakeRow(new String[] {"image_count"}, new Object[] {5})); }
        };
        assertEquals(-1, ts.getGlobalImageCount());
        org.calacademy.antweb.geolocale.GeolocaleTaxon withNull = new org.calacademy.antweb.geolocale.GeolocaleTaxon() {
            { readGlobalImageCount(fakeRow(new String[] {"image_count", "global_image_count"}, new Object[] {5, null})); }
        };
        assertEquals(-1, withNull.getGlobalImageCount());
        org.calacademy.antweb.geolocale.GeolocaleTaxon with = new org.calacademy.antweb.geolocale.GeolocaleTaxon() {
            { readGlobalImageCount(fakeRow(new String[] {"image_count", "GLOBAL_IMAGE_COUNT"}, new Object[] {5, 42})); }
        };
        assertEquals(42, with.getGlobalImageCount());
        org.calacademy.antweb.geolocale.GeolocaleTaxon broken = new org.calacademy.antweb.geolocale.GeolocaleTaxon() {
            { readGlobalImageCount(null); }
        };
        assertEquals(-1, broken.getGlobalImageCount());
    }

    /** One row of a result set (column labels and values), enough for readGlobalImageCount. */
    private static java.sql.ResultSet fakeRow(String[] labels, Object[] values) {
        final Object[] last = new Object[1];
        java.sql.ResultSetMetaData md = (java.sql.ResultSetMetaData) java.lang.reflect.Proxy.newProxyInstance(
            ImagesAnywhereTest.class.getClassLoader(), new Class<?>[] {java.sql.ResultSetMetaData.class}, (proxy, m, args) -> {
                if (m.getName().equals("getColumnCount")) return labels.length;
                if (m.getName().equals("getColumnLabel") || m.getName().equals("getColumnName")) return labels[(Integer) args[0] - 1];
                throw new UnsupportedOperationException(m.getName());
            });
        return (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
            ImagesAnywhereTest.class.getClassLoader(), new Class<?>[] {java.sql.ResultSet.class}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getMetaData": return md;
                    case "getInt":
                        for (int i = 0; i < labels.length; i++) {
                            if (labels[i].equalsIgnoreCase((String) args[0])) {
                                last[0] = values[i];
                                return values[i] == null ? 0 : (Integer) values[i];
                            }
                        }
                        throw new java.sql.SQLException("Column '" + args[0] + "' not found.");
                    case "wasNull": return last[0] == null;
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });
    }

    /** Nightly set 4 counts the allantwebants rows' Images with the live projects, once, never the Port of Entry. */
    public void testSetFourCountsTheAllAntwebRows() {
        org.calacademy.antweb.Project all = new org.calacademy.antweb.Project(org.calacademy.antweb.Project.ALLANTWEBANTS);
        org.calacademy.antweb.Project ca = new org.calacademy.antweb.Project("californiaants");
        org.calacademy.antweb.Project poe = new org.calacademy.antweb.Project(org.calacademy.antweb.Project.PORT_OF_ENTRY);
        List<String> names = new ArrayList<>();
        for (org.calacademy.antweb.Project p : org.calacademy.antweb.home.ProjTaxonCountDb.imageCrawlProjects(Arrays.asList(ca, poe), all)) names.add(p.getName());
        assertEquals(Arrays.asList("californiaants", "allantwebants"), names);
        names.clear();
        for (org.calacademy.antweb.Project p : org.calacademy.antweb.home.ProjTaxonCountDb.imageCrawlProjects(Arrays.asList(all, ca), all)) names.add(p.getName());
        assertEquals(Arrays.asList("allantwebants", "californiaants"), names);
        assertEquals(1, org.calacademy.antweb.home.ProjTaxonCountDb.imageCrawlProjects(null, all).size());
        assertEquals(0, org.calacademy.antweb.home.ProjTaxonCountDb.imageCrawlProjects(null, null).size());
    }

    /** Brian Fisher 2026-10-27: every Images column counts images of the ant; labels never count. */
    public void testAntImagesShownLeaveLabelsOut() {
        Map<String, org.calacademy.antweb.SpecimenImage> images = new HashMap<>();
        String[][] shots = {{"h", "1"}, {"p", "1"}, {"l", "1"}, {"L", "2"}, {"d", "0"}};
        for (String[] s : shots) {
            org.calacademy.antweb.SpecimenImage i = new org.calacademy.antweb.SpecimenImage();
            i.setShot(s[0]);
            i.setNumber(Integer.parseInt(s[1]));
            images.put(s[0] + s[1], i);
        }
        assertEquals("head and profile; the labels and a shot without number left out", 2, ImagesAnywhere.antImagesShown(images));
        assertEquals(0, ImagesAnywhere.antImagesShown(null));
        assertTrue(StatisticsDb.ANT_IMAGES_OF_SPECIMEN, StatisticsDb.ANT_IMAGES_OF_SPECIMEN.contains(StatisticsDb.ANT_IMAGE));
        assertTrue(StatisticsDb.ANT_IMAGES_OF_SPECIMEN.startsWith(" left join image i on i.image_of_id = s.code"));
    }

    /** Brian Fisher 2026-10-27: "5 images + label" for a single specimen (ImageCounts). */
    public void testSpecimenImagesShowLabelsApart() {
        assertEquals("No Images", org.calacademy.antweb.ImageCounts.text(0, 0));
        assertEquals("Label only", org.calacademy.antweb.ImageCounts.text(0, 1));
        assertEquals("Label only (2 labels)", org.calacademy.antweb.ImageCounts.text(0, 2));
        assertEquals("<span class='numbers'>5</span> Images", org.calacademy.antweb.ImageCounts.text(5, 0));
        assertEquals("<span class='numbers'>5</span> Images + label", org.calacademy.antweb.ImageCounts.text(5, 1));
        assertEquals("<span class='numbers'>5</span> Images + 2 labels", org.calacademy.antweb.ImageCounts.text(5, 2));
        assertTrue(org.calacademy.antweb.ImageCounts.isLabel(" L "));
        assertFalse(org.calacademy.antweb.ImageCounts.isLabel("h"));
    }

    public void testListImagesArePendingUntilANightlyCountsAntImages() {
        assertFalse(org.calacademy.antweb.util.CountCheck.antImagesRuleCounted(Arrays.asList("  Set 4 done (numbers counted by the rules of 2026-10-21)")));
        assertTrue(org.calacademy.antweb.util.CountCheck.antImagesRuleCounted(Arrays.asList("x", "  Set 4 done (numbers counted by the rules of 2026-10-21; "
            + StatisticsDb.ANT_IMAGES_RULE_LOG + ")")));
    }

    public void testImagedCastesQueryLeavesLabelsOut() {
        String sql = ImagedCastes.sql();
        assertTrue(sql, sql.contains(StatisticsDb.ANT_IMAGE));
        assertTrue(sql, sql.contains("s.taxon_name = ?"));
        assertTrue(sql, sql.contains("count(distinct s.code)"));
    }

    public void testImagedCastesLine() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"worker", "normal", "9"});
        rows.add(new String[] {"worker", "major/soldier", "3"});
        rows.add(new String[] {"queen", "", "2"});
        rows.add(new String[] {"male", "alate", "1"});
        rows.add(new String[] {"", "", "4"});
        LinkedHashMap<String, LinkedHashMap<String, Integer>> counts = ImagedCastes.group(rows);
        String html = ImagedCastes.toHtml(counts, "/images.do?genus=g&species=s&rank=species");
        assertTrue(html, html.contains("data-worker=\"12\" data-queen=\"2\" data-male=\"1\" data-other=\"0\" data-unknown=\"4\""));
        assertTrue(html, html.contains(">worker</a> (12 specimens: 9 <a"));
        // the ordinary worker subcaste is shown as "worker", linked to the filter "normal" (Brian Fisher 2026-10-27)
        assertTrue(html, html.contains("caste=normal\">worker</a>, 3 <a"));
        assertFalse(html, html.contains(">normal<"));
        assertTrue(html, html.contains("caste=majorSoldier"));
        assertTrue(html, html.contains(">queen</a> (2)"));
        assertTrue(html, html.contains(">male</a> (1)"));      // one subcaste only: not broken down
        assertTrue(html, html.contains("caste not given (4)"));
        assertFalse(html, html.contains(">other<"));
        assertTrue(html, html.contains("caste=queen"));
    }

    public void testImagedCastesNone() {
        String html = ImagedCastes.toHtml(ImagedCastes.group(new ArrayList<>()), null);
        assertTrue(html, html.contains("data-worker=\"0\""));
        assertTrue(html, html.contains("no specimen of this name has an image of the ant yet"));
    }
}

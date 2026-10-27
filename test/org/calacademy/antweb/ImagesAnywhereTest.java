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

    public void testImagesColumnShowsBothNumbers() {
        assertEquals("<span class='images_total'>4</span> Images (<span class='numbers'>0</span> from Comoros)",
            ImagesAnywhere.imagesColumn(4, 0, "from Comoros"));
        assertEquals("<span class='images_total'>12</span> Images (<span class='numbers'>3</span> in CASC)",
            ImagesAnywhere.imagesColumn(12, 3, "in CASC"));
    }

    public void testImagesColumnOneNumberWhenThePlaceHasThemAll() {
        assertEquals("<span class='numbers'>5</span> Images", ImagesAnywhere.imagesColumn(5, 5, "from Comoros"));
        // a project (no place phrase) and a total not known show the place's number
        assertEquals("<span class='numbers'>5</span> Images", ImagesAnywhere.imagesColumn(9, 5, null));
        assertEquals("<span class='numbers'>5</span> Images", ImagesAnywhere.imagesColumn(-1, 5, "from Comoros"));
    }

    public void testNoImagesOnlyWhenThereIsNoImageInAntWeb() {
        assertEquals("No Images", ImagesAnywhere.imagesColumn(0, 0, "from Comoros"));
        assertEquals("No Images", ImagesAnywhere.imagesColumn(-1, 0, "from Comoros"));
        assertFalse(ImagesAnywhere.imagesColumn(1, 0, "from Comoros").startsWith("No "));
    }

    private static org.calacademy.antweb.TaxonSet row(String rank, int global, boolean live, int place) {
        org.calacademy.antweb.geolocale.GeolocaleTaxon ts = new org.calacademy.antweb.geolocale.GeolocaleTaxon();
        ts.setRank(rank);
        ts.setGlobalImageCount(global);
        ts.setGlobalImageCountLive(live);
        ts.setImageCount(place);
        return ts;
    }

    /** PR #291 staging run 2: species rows said "No Images" (Comoros Leptogenys pavesii, 20 images in AntWeb) and the
     *  place's number alone (Monomorium floricola "8 Images", 137 in AntWeb): the allantwebants row read for the first
     *  number was 0. Species and subspecies rows count it live from the image table. */
    public void testSpeciesRowsCountTheImagesInAntWebLive() {
        org.calacademy.antweb.TaxonSet species = row("species", -1, false, 0);
        String col = species.globalImageCountColumn("ponerinaeleptogenys pavesii");
        assertTrue(col, col.contains("from specimen s join image i on i.image_of_id = s.code"));
        assertTrue(col, col.contains("s.taxon_name = 'ponerinaeleptogenys pavesii'"));
        assertTrue(col, col.contains(StatisticsDb.ANT_IMAGE));
        assertTrue(col, col.endsWith(" global_image_count"));
        assertFalse(col, col.contains("allantwebants"));
        assertFalse(row("subspecies", -1, false, 0).globalImageCountColumn("x").contains("allantwebants"));
        String genus = row("genus", -1, false, 0).globalImageCountColumn("ponerinaeleptogenys");
        assertTrue(genus, genus.contains("project_name = 'allantwebants'"));
    }

    public void testImagesColumnOfARow() {
        assertEquals("<span class='images_total'>20</span> Images (<span class='numbers'>0</span> from Comoros)",
            ImagesAnywhere.imagesColumn(row("species", 20, true, 0), "from Comoros"));
        assertEquals("<span class='images_total'>137</span> Images (<span class='numbers'>8</span> from Anguilla)",
            ImagesAnywhere.imagesColumn(row("species", 137, true, 8), "from Anguilla"));
        // the place's row counted before the ant-only rule (labels included) cannot exceed the live AntWeb number
        assertEquals("<span class='numbers'>49</span> Images",
            ImagesAnywhere.imagesColumn(row("species", 49, true, 56), "from Comoros"));
        // a project's list counts images from anywhere: the live number
        assertEquals("<span class='numbers'>12</span> Images", ImagesAnywhere.imagesColumn(row("species", 12, true, 0), null));
        // no image of the ant in AntWeb
        assertEquals("No Images", ImagesAnywhere.imagesColumn(row("species", 0, true, 3), "from Comoros"));
        // a genus row (allantwebants row, nightly): the higher of the two, as before
        assertEquals("<span class='numbers'>7</span> Images", ImagesAnywhere.imagesColumn(row("genus", 0, false, 7), "from Comoros"));
        assertEquals("No Images", ImagesAnywhere.imagesColumn((org.calacademy.antweb.TaxonSet) null, "from Comoros"));
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

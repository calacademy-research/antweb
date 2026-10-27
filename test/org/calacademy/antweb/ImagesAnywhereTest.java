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

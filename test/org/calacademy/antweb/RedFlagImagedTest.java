package test.org.calacademy.antweb;

import java.util.*;

import junit.framework.TestCase;

import org.calacademy.antweb.*;

/**
 * A red-flagged specimen's photos are not counted as imaged (Brian Fisher, 2026-11-09), on species pages too:
 * the specimens page header "(M imaged)" (showBrowse-body.jsp, Specimen.countsAsImaged) and the images page header
 * (Taxon.getUniqueChildImagesCount). 2026-11-17: Myrmica (indet) said 28 imaged on its page and 22 on the genus page's
 * indet line, which counts in SQL without red-flagged specimens. No database.
 */
public class RedFlagImagedTest extends TestCase {

    private static Specimen specimen(String code, String flag, String... shots) {
        Specimen s = new Specimen();
        s.setCode(code);
        s.setFlag(flag);
        Hashtable<String, SpecimenImage> images = new Hashtable<>();
        for (String shot : shots) {
            SpecimenImage i = new SpecimenImage();
            i.setShot(shot);
            i.setNumber(1);
            i.setCode(code);
            images.put(shot + "1", i);
        }
        s.setImages(images);
        return s;
    }

    public void testRedFlag() {
        assertTrue(specimen("a", "red").isRedFlagged());
        assertTrue(specimen("a", " Red ").isRedFlagged());
        assertFalse(specimen("a", null).isRedFlagged());
        assertFalse(specimen("a", "yellow").isRedFlagged());
    }

    public void testCountsAsImaged() {
        assertTrue(Specimen.countsAsImaged(specimen("a", null, "h")));
        assertFalse(Specimen.countsAsImaged(specimen("b", "red", "h")));
        assertFalse(Specimen.countsAsImaged(specimen("c", null, "l")));   // labels never count
        assertFalse(Specimen.countsAsImaged(specimen("d", null)));
        assertFalse(Specimen.countsAsImaged(null));
    }

    public void testImagesHeaderLeavesRedFlaggedOut() {
        Species species = new Species();
        species.setRank(Rank.SPECIES);
        ArrayList<Taxon> children = new ArrayList<>();
        children.add(specimen("a", null, "h", "p"));
        children.add(specimen("b", "red", "h"));
        children.add(specimen("c", null, "d"));
        children.add(specimen("e", null, "l"));
        species.setChildren(children);
        assertEquals(2, species.getUniqueChildImagesCount("p", "h", "d"));
    }
}

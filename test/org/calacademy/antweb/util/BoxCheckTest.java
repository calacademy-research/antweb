package test.org.calacademy.antweb.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.geolocale.LocalityOverview;
import org.calacademy.antweb.util.BoxCheck;

/**
 * The daily box check (BoxCheck, 2026-11-18): a state whose box flags many of its own specimens is listed, a state
 * with a few badly georeferenced specimens is not. Betsiboka's old box (before 2026-11-18) flagged Phil Ward's correct
 * UC Davis records.
 */
public class BoxCheckTest extends TestCase {

    /** A box checked as the upload checks it (LocalityOverview.isWithinBounds: box plus 0.2 degree). */
    static BoxCheck.Box box(double left, double bottom, double right, double top) {
        return new BoxCheck.Box() {
            public boolean hasBox() { return true; }
            public boolean isWithin(double lat, double lon) {
                return LocalityOverview.isWithinBounds(left, bottom, right, top, lat, lon);
            }
        };
    }

    static final BoxCheck.Box NO_BOX = new BoxCheck.Box() {
        public boolean hasBox() { return false; }
        public boolean isWithin(double lat, double lon) { return true; }
    };

    public void testWrongBoxListedFewStraysNot() {
        Map<String, BoxCheck.Tally> t = new LinkedHashMap<>();
        // A box too far east: 40 of 50 specimens out of bounds
        BoxCheck.Box wrong = box(46.94, -18.5, 48.0, -16.0);
        BoxCheck.add(t, "Madagascar", "Betsiboka", wrong, -17.0, 46.5, 40);
        BoxCheck.add(t, "Madagascar", "Betsiboka", wrong, -17.0, 47.0, 10);
        // A right box with 12 strays out of 500 (2%)
        BoxCheck.Box right = box(-124.4, 32.5, -114.1, 42.0);
        BoxCheck.add(t, "United States", "California", right, 37.0, -122.0, 488);
        BoxCheck.add(t, "United States", "California", right, 10.0, -84.0, 12);
        // 5 of 6 out of bounds: too few to say the box is wrong
        BoxCheck.Box small = box(10.0, 10.0, 11.0, 11.0);
        BoxCheck.add(t, "Niger", "Agadez", small, 17.0, 8.0, 5);
        BoxCheck.add(t, "Niger", "Agadez", small, 10.5, 10.5, 1);
        // no box: counted, never flagged; not a valid state: not counted
        BoxCheck.add(t, "Niger", "Tahoua", NO_BOX, 15.0, 5.0, 30);
        BoxCheck.add(t, "Niger", "Nowhere", null, 15.0, 5.0, 30);

        List<BoxCheck.Tally> wrongBoxes = BoxCheck.wrongBoxes(t.values(), BoxCheck.MIN_FLAGGED, BoxCheck.PERCENT);
        assertEquals(1, wrongBoxes.size());
        assertEquals("Betsiboka", wrongBoxes.get(0).adm1);
        assertEquals(40, wrongBoxes.get(0).flagged);
        assertEquals(50, wrongBoxes.get(0).specimens);
        assertEquals(12, t.get("United States\tCalifornia").flagged);
        assertEquals(0, t.get("Niger\tTahoua").flagged);
        assertNull(t.get("Niger\tNowhere"));
        assertEquals(1, BoxCheck.withoutBox(t.values()));

        String line = BoxCheck.line(wrongBoxes, BoxCheck.withoutBox(t.values()));
        assertTrue(line, line.startsWith("Box check: 1 state's box flags many of its own specimens"));
        assertTrue(line, line.contains("Betsiboka (Madagascar) 40 of 50"));
        assertTrue(line, line.endsWith("; 1 states with specimens have no box (not checked)"));
    }

    public void testAllWellAndThreshold() {
        Map<String, BoxCheck.Tally> t = new LinkedHashMap<>();
        BoxCheck.Box b = box(0.0, 0.0, 1.0, 1.0);
        BoxCheck.add(t, "A", "a", b, 0.5, 0.5, 40);
        BoxCheck.add(t, "A", "a", b, 5.0, 5.0, 10);     // exactly 20% and 10 specimens: listed
        BoxCheck.add(t, "B", "b", b, 0.5, 0.5, 41);
        BoxCheck.add(t, "B", "b", b, 5.0, 5.0, 10);     // just under 20%: not listed
        List<BoxCheck.Tally> w = BoxCheck.wrongBoxes(t.values(), BoxCheck.MIN_FLAGGED, BoxCheck.PERCENT);
        assertEquals(1, w.size());
        assertEquals("a", w.get(0).adm1);
        assertEquals("Box check: ok", BoxCheck.line(BoxCheck.wrongBoxes(new java.util.ArrayList<>(), 10, 20), 0));
    }

    public void testBoxAcrossTheDateLine() {
        Map<String, BoxCheck.Tally> t = new LinkedHashMap<>();
        BoxCheck.Box fiji = box(177.0, -21.0, -178.0, -12.0);   // left > right: wraps the 180 meridian
        BoxCheck.add(t, "Fiji", "Eastern", fiji, -18.0, 179.5, 20);
        BoxCheck.add(t, "Fiji", "Eastern", fiji, -18.0, -179.5, 20);
        assertEquals(0, t.get("Fiji\tEastern").flagged);
    }

    public void testListedAtMostTen() {
        Map<String, BoxCheck.Tally> t = new LinkedHashMap<>();
        BoxCheck.Box b = box(0.0, 0.0, 1.0, 1.0);
        for (int i = 0; i < 12; i++) BoxCheck.add(t, "C", "s" + i, b, 5.0, 5.0, 20 + i);
        List<BoxCheck.Tally> w = BoxCheck.wrongBoxes(t.values(), BoxCheck.MIN_FLAGGED, BoxCheck.PERCENT);
        assertEquals(12, w.size());
        assertEquals("s11", w.get(0).adm1);   // worst first
        assertTrue(BoxCheck.line(w, 0).contains("; and 2 more."));
    }
}

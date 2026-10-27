package test.org.calacademy.antweb.util;

import java.time.LocalDate;

import junit.framework.TestCase;

import org.calacademy.antweb.geolocale.LatLon;
import org.calacademy.antweb.home.DescEditDb;
import org.calacademy.antweb.util.CountCheckAction;

/**
 * Small fixes of 2026-10-27 that need no database:
 * - the count check's rotating sample counts days from 1970 (it used the day of the year, which restarts at 1 on
 *   1 January, so some slices were skipped or waited 35 days across the new year);
 * - LatLon.isValid: a latitude over 90 was valid when the longitude was in range;
 * - DescEditDb.httpSecurify: page text starting with http:// or ending with an address made the taxon page fail.
 */
public class AdminDataFixesTest extends TestCase {

    public void testRotationDayRunsOnAcrossTheNewYear() {
        LocalDate d = LocalDate.of(2026, 12, 20);
        for (int i = 0; i < 30; i++) {
            assertEquals(CountCheckAction.rotationDay(d) + 1, CountCheckAction.rotationDay(d.plusDays(1)));
            d = d.plusDays(1);
        }
        // every 7-day window gives the 7 country slices and every 30-day window the 30 adm1 slices
        LocalDate start = LocalDate.of(2026, 12, 15);
        for (int w = 0; w < 40; w++) {
            java.util.Set<Integer> week = new java.util.HashSet<>();
            java.util.Set<Integer> month = new java.util.HashSet<>();
            for (int i = 0; i < 30; i++) {
                int day = CountCheckAction.rotationDay(start.plusDays(w + i));
                if (i < 7) week.add(day % 7);
                month.add(day % 30);
            }
            assertEquals(7, week.size());
            assertEquals(30, month.size());
        }
    }

    public void testLatLonOutOfRangeIsInvalid() {
        assertFalse(new LatLon(95, 10).isValid());
        assertFalse(new LatLon(-91, 0).isValid());
        assertFalse(new LatLon(10, 200).isValid());
        assertFalse(new LatLon(10, -181).isValid());
        assertTrue(new LatLon(-18.9, 47.5).isValid());
    }

    public void testHttpSecurifyHandlesAnyText() {
        assertEquals("https://x.org/a.jpg", DescEditDb.httpSecurify("http://x.org/a.jpg"));
        assertEquals("see https://x.org", DescEditDb.httpSecurify("see http://x.org"));
        assertEquals("<a href=\"https://a.org/\">a</a> <img src='https://b.org/b.jpg'>",
            DescEditDb.httpSecurify("<a href=\"http://a.org/\">a</a> <img src='http://b.org/b.jpg'>"));
        assertEquals("no address", DescEditDb.httpSecurify("no address"));
        assertNull(DescEditDb.httpSecurify(null));
    }
}

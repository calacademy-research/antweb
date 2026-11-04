package test.org.calacademy.antweb.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.OperationDetails;
import org.calacademy.antweb.search.AdvancedSearchForm;
import org.calacademy.antweb.util.CountCheck;
import org.calacademy.antweb.util.CountCheckAction;
import org.calacademy.antweb.util.CountInvariants;

/**
 * Count check page and small fixes of 2026-11-04, without a database:
 *
 *   html             the per-place table counts a difference of the same place made by the invariants (equal
 *                    Place, other object); the headings add the differences not listed, as the final line does
 *   lookupFailed     a table lookup that fails is a note on the page (escaped, once), not a silently skipped check
 *   OperationDetails augment and the minutes text with null messages or no time
 *   AdvancedSearchForm.setAdm2s keeps the value
 */
public class AuditTmFixesTest extends TestCase {

    private static CountCheck.Mismatch mismatch(CountCheck.Place place, String check, String name, int stored, int recount, String pending)
            throws Exception {
        Constructor<CountCheck.Mismatch> c = CountCheck.Mismatch.class.getDeclaredConstructor(
            CountCheck.Place.class, String.class, String.class, int.class, int.class, String.class);
        c.setAccessible(true);
        return c.newInstance(place, check, name, stored, recount, pending);
    }

    private static String html(CountCheck.Result result) throws Exception {
        Method m = CountCheckAction.class.getDeclaredMethod("html", CountCheck.Result.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, result, "https://example.org");
    }

    public void testHtmlPerPlaceAndHeadings() throws Exception {
        CountCheck.Result result = new CountCheck.Result();
        result.countedUntil = new Timestamp(0);
        CountCheck.Place madagascar = new CountCheck.Place(CountCheck.GEOLOCALE, "126", "Madagascar", "country", "Madagascar");
        result.places.add(madagascar);
        result.rowsChecked.put(madagascar, 10);
        // the invariants' own Place object for the same place
        CountCheck.Place same = new CountCheck.Place(CountCheck.GEOLOCALE, "126", "Madagascar", null, null);
        result.mismatches.add(mismatch(same, "place.totals", "Genera", 5, 6, null));
        result.mismatches.add(mismatch(madagascar, "place.totals", "Species", 5, 6, "an upload"));
        result.unlisted.put("list.genus", 4);
        result.unlisted.put("list.genus (pending)", 2);

        String h = html(result);
        assertTrue(h, h.contains("<tr><td>Madagascar</td><td>10</td><td>1</td><td>1</td></tr>"));
        assertTrue(h, h.contains("<h3>Real differences (5, of which 4 not listed below)</h3>"));
        assertTrue(h, h.contains("<h3>Pending differences (3, of which 2 not listed below)</h3>"));
        assertTrue(h, h.contains("data-real=\"5\" data-pending=\"3\""));

        CountCheck.Result plain = new CountCheck.Result();
        plain.countedUntil = new Timestamp(0);
        String p = html(plain);
        assertTrue(p, p.contains("<h3>Real differences (0)</h3>"));
    }

    public void testLookupFailedIsANote() throws Exception {
        Connection failing = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) throw new SQLException("<b>gone</b>");
                throw new UnsupportedOperationException(method.getName());
            });
        CountCheck.Result result = new CountCheck.Result();
        CountInvariants inv = new CountInvariants(failing, result, null, true);
        Method m = CountInvariants.class.getDeclaredMethod("tableExistsQuietly", String.class);
        m.setAccessible(true);
        assertEquals(Boolean.FALSE, m.invoke(inv, "statistics"));
        assertEquals(Boolean.FALSE, m.invoke(inv, "statistics"));
        assertEquals(1, result.notes.size());
        String note = result.notes.get(0);
        assertTrue(note, note.contains("statistics") && note.contains("skipped"));
        assertFalse(note, note.contains("<b>"));
    }

    public void testOperationDetailsNullSafe() {
        OperationDetails a = new OperationDetails("op", null);
        a.augment(new OperationDetails("op", "failed."));
        assertEquals("failed.", a.getMessage());
        a.augment(new OperationDetails("op", null));
        a.augment(null);
        a.augment(new OperationDetails("op", "success"));
        assertEquals("failed.", a.getMessage());
        assertEquals("", new OperationDetails().getExecTimeMin());
    }

    public void testAdm2sSetter() {
        AdvancedSearchForm f = new AdvancedSearchForm();
        f.setAdm2s(new String[] {"Marin"});
        assertEquals("Marin", f.getAdm2s()[0]);
    }
}

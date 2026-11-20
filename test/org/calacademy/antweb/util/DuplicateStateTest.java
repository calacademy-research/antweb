package test.org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.geolocale.EditGeolocaleAction;
import org.calacademy.antweb.curate.geolocale.EditGeolocaleForm;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.util.BoxCheck;
import org.calacademy.antweb.util.GeolocaleMgr;

/**
 * Two valid states of one country written alike (2026-11-19). That day 269 such pairs from the 2021 Geonames and
 * Geonet loads (Nimba County and Nimba, Grand-Est and Grand Est, Murang'A and Murang'a) were merged: nothing had
 * stopped an administrator from making the second one valid, and nothing reported it. Now:
 *   - the Geolocale Manager refuses to save a state as valid when its country already has a valid state written alike
 *     (EditGeolocaleAction.duplicateState, through GeolocaleMgr.validStateWrittenAlike);
 *   - the daily update's box check lists every such pair and ends yellow (BoxCheck.run, through
 *     GeolocaleMgr.duplicateValidStates and BoxCheck.duplicatesLine).
 * "Written alike" is the upload's rule (GeolocaleMgr.adm1SpellingKey: capitals, accents, blanks, punctuation). No database.
 */
public class DuplicateStateTest extends TestCase {

    private static Adm1 adm1(int id, String name, String country, boolean valid) {
        Adm1 a = new Adm1();
        a.setId(id);
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(country);
        a.setIsValid(valid);
        return a;
    }

    private static List<Geolocale> places() {
        return new ArrayList<>(Arrays.asList(
            adm1(5595, "Murang'a", "Kenya", true),
            adm1(2849, "Murang'A", "Kenya", false),          // merged: an old name now
            adm1(1119, "Nairobi", "Kenya", true),
            adm1(2858, "Taita Taveta", "Kenya", true),
            adm1(7443, "Grand Est", "France", true),
            adm1(3317, "Kisumu", "Kenya", true),
            adm1(9001, "Kisii", "Kenya", true)));             // one letter apart, two real places
    }

    public void testTheManagerFindsTheValidStateWrittenAlike() {
        List<Geolocale> places = places();
        // making the merged row valid again: Murang'a is already valid
        assertEquals("Murang'a", GeolocaleMgr.validStateWrittenAlike(places, "Kenya", "Murang'A", 2849).getName());
        assertEquals("Taita Taveta", GeolocaleMgr.validStateWrittenAlike(places, "Kenya", "Taita/Taveta", 7597).getName());
        assertEquals("Grand Est", GeolocaleMgr.validStateWrittenAlike(places, "France", "GRAND-EST", 2504).getName());
        // the row itself, another word, another country, a different place: no
        assertNull(GeolocaleMgr.validStateWrittenAlike(places, "Kenya", "Murang'a", 5595));
        assertNull(GeolocaleMgr.validStateWrittenAlike(places, "Kenya", "Nairobi City", 7596));
        assertNull(GeolocaleMgr.validStateWrittenAlike(places, "Uganda", "Nairobi", 1));
        assertNull(GeolocaleMgr.validStateWrittenAlike(places, "Kenya", "Kisii", 9001));
        assertNull(GeolocaleMgr.validStateWrittenAlike(places, "Kenya", null, 1));
    }

    public void testTheDailyCheckListsThePairs() {
        List<Geolocale> places = places();
        assertEquals(new ArrayList<String>(), GeolocaleMgr.duplicateValidStates(places));
        assertEquals("", BoxCheck.duplicatesLine(GeolocaleMgr.duplicateValidStates(places)));
        places.add(adm1(7598, "Tharaka-Nithi", "Kenya", true));
        places.add(adm1(2859, "Tharaka - Nithi", "Kenya", true));
        places.add(adm1(2504, "Grand-Est", "France", true));
        places.add(adm1(1, "Grand Est", "Germany", true));      // another country: not a pair
        assertEquals(Arrays.asList("France: Grand Est / Grand-Est", "Kenya: Tharaka - Nithi / Tharaka-Nithi"),
            GeolocaleMgr.duplicateValidStates(places));
        String line = BoxCheck.duplicatesLine(GeolocaleMgr.duplicateValidStates(places));
        assertTrue(line, line.startsWith("; DUPLICATE STATES: 2 pairs of valid states written alike"));
        assertTrue(line, line.endsWith("France: Grand Est / Grand-Est; Kenya: Tharaka - Nithi / Tharaka-Nithi"));
    }

    public void testALongListIsCut() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) many.add("Country" + i + ": A / A.");
        String line = BoxCheck.duplicatesLine(many);
        assertTrue(line, line.contains("13 pairs") && line.endsWith("; and 3 more"));
        assertEquals("; DUPLICATE STATES: 1 pair of valid states written alike (make one an old name of the other in the Geolocale Manager): X: a / A",
            BoxCheck.duplicatesLine(Arrays.asList("X: a / A")));
    }

    private static EditGeolocaleForm form(String name, String parent, boolean valid) {
        EditGeolocaleForm f = new EditGeolocaleForm();
        f.setName(name);
        f.setParent(parent);
        f.setIsValid(valid);
        return f;
    }

    // review of PR #383: only a save that makes a state valid, or renames or moves a valid one, is checked
    public void testTheManagerRefusesOnlyANewDuplicate() {
        List<Geolocale> places = places();
        Adm1 merged = adm1(2849, "Murang'A", "Kenya", false);
        // making the merged row valid again: refused, and the message names the valid state
        String m = EditGeolocaleAction.duplicateState(form("Murang'A", "Kenya", true), merged, places);
        assertTrue(m, m != null && m.contains("Murang&#39;a (id 5595)"));
        // saved as not valid: no check
        assertNull(EditGeolocaleAction.duplicateState(form("Murang'A", "Kenya", false), merged, places));
        // a pair that exists already: a valid state's box or note can still be saved
        places.add(adm1(2859, "Tharaka - Nithi", "Kenya", true));
        Adm1 twin = adm1(7598, "Tharaka-Nithi", "Kenya", true);
        places.add(twin);
        assertNull(EditGeolocaleAction.duplicateState(form("Tharaka-Nithi", "Kenya", true), twin, places));
        assertNull(EditGeolocaleAction.duplicateState(form(null, null, true), twin, places));
        // renaming a valid state onto another one written alike: refused
        Adm1 nairobi = (Adm1) places.get(2);
        assertNotNull(EditGeolocaleAction.duplicateState(form("Taita-Taveta", "Kenya", true), nairobi, places));
        // moving a valid state to a country that has one written alike: checked against the new country
        Adm1 grandEst = adm1(2504, "Grand Est", "Germany", true);
        assertNotNull(EditGeolocaleAction.duplicateState(form("Grand Est", "France", true), grandEst, places));
        assertNull(EditGeolocaleAction.duplicateState(form("Grand Est", "Germany", true), grandEst, places));
        // a country or region, or no row: no check
        assertNull(EditGeolocaleAction.duplicateState(form("Kenya", null, true), null, places));
        // a stored name with a blank at the end, saved unchanged: not a rename
        Adm1 blank = adm1(2860, "Taita Taveta ", "Kenya", true);
        assertNull(EditGeolocaleAction.duplicateState(form("Taita Taveta ", "Kenya", true), blank, places));
        // before the places are loaded (no list): a save that needs the check waits; one that does not goes ahead
        assertEquals(EditGeolocaleAction.PLACES_LOADING, EditGeolocaleAction.duplicateState(form("Murang'A", "Kenya", true), merged, null));
        assertNull(EditGeolocaleAction.duplicateState(form("Murang'A", "Kenya", false), merged, null));
        assertNull(EditGeolocaleAction.duplicateState(form("Tharaka-Nithi", "Kenya", true), twin, null));      // a box fixed
    }
}

package test.org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

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
}

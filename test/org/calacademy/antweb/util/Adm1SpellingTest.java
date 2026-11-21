package test.org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.util.GeolocaleMgr;

/**
 * The state (adm1) the specimen upload stores (2026-11-19). That day 269 duplicate valid states (one row from the 2021
 * Geonames load, one from the Geonet load: Nimba County and Nimba, Grand-Est and Grand Est) were merged: the duplicate
 * became an old name of the kept state (not valid, valid_name = the kept state), Brian Fisher: "we need synonyms to
 * translate anyone use of the invalid member". The upload found the valid state of an old name but stored the name as
 * the file wrote it, so a re-upload put "Nimba County" back on the specimen and took it off Nimba's page. Now:
 *   - a valid state is stored as it is;
 *   - an old name is stored as its valid state;
 *   - a name written like a valid state or an old name with other capitals, accents, blanks or punctuation is stored
 *     as that valid state ("grand-est", "NIMBA COUNTY", "Murang'A");
 *   - a key two valid states share is not used; another word ("St. Elizabeth" for Saint Elizabeth) is not a spelling;
 *     a name of another country's state is not used;
 *   - the report sections are in the Recognized group, not counted as corrected (2026-11-21, Brian Fisher).
 * No database: the rows are made here (GeolocaleMgr.adm1sBySpelling, adm1ToStore). IslandParseLineTest runs the same
 * through SpecimenUploadParse.parseLine.
 */
public class Adm1SpellingTest extends TestCase {

    private final MultiKeyMap<String, Adm1> adm1s = new MultiKeyMap<>();
    private final List<Adm1> rows = new ArrayList<>();

    private void adm1(String name, String country, boolean valid, String validName) {
        Adm1 a = new Adm1();
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(country);
        a.setIsValid(valid);
        a.setValidName(validName);
        adm1s.put(name, country, a);
        rows.add(a);
    }

    @Override
    protected void setUp() {
        // production's rows after the merges of 2026-11-19
        adm1("Nimba", "Liberia", true, null);
        adm1("Nimba County", "Liberia", false, "Nimba");
        adm1("Grand Est", "France", true, null);
        adm1("Grand-Est", "France", false, "Grand Est");
        adm1("Murang'a", "Kenya", true, null);
        adm1("Murang'A", "Kenya", false, "Murang'a");
        adm1("Taita Taveta", "Kenya", true, null);
        adm1("Taita/Taveta", "Kenya", false, "Taita Taveta");
        adm1("Saint Elizabeth", "Jamaica", true, null);
        adm1("St. Elizabeth", "Jamaica", false, "Saint Elizabeth");
        adm1("Lima", "Peru", true, null);
        adm1("Lima region", "Peru", false, "Lima");
        adm1("Toliara", "Madagascar", false, null);              // an old unit split up: leads nowhere
        adm1("Atsimo-Andrefana", "Madagascar", true, null);
    }

    private String stored(String name, String country) {
        Map<String, Adm1> bySpelling = GeolocaleMgr.adm1sBySpelling(rows, adm1s);
        Adm1 a = GeolocaleMgr.adm1ToStore(adm1s, bySpelling, name, country);
        return a == null ? null : a.getName();
    }

    public void testValidStatesAreStoredAsTheyAre() {
        assertEquals("Nimba", stored("Nimba", "Liberia"));
        assertEquals("Grand Est", stored("Grand Est", "France"));
        assertEquals("Murang'a", stored("Murang'a", "Kenya"));
    }

    public void testAnOldNameIsStoredAsItsValidState() {
        assertEquals("Nimba", stored("Nimba County", "Liberia"));
        assertEquals("Grand Est", stored("Grand-Est", "France"));
        assertEquals("Taita Taveta", stored("Taita/Taveta", "Kenya"));
        assertEquals("Saint Elizabeth", stored("St. Elizabeth", "Jamaica"));
        assertEquals("Lima", stored("Lima region", "Peru"));
    }

    public void testOtherCapitalsAccentsBlanksAndPunctuation() {
        assertEquals("Nimba", stored("NIMBA COUNTY", "Liberia"));
        assertEquals("Nimba", stored("  nimba  county ", "Liberia"));
        assertEquals("Grand Est", stored("grand-est", "France"));
        assertEquals("Grand Est", stored("GRAND EST.", "France"));
        assertEquals("Murang'a", stored("Muranga", "Kenya"));
        assertEquals("Murang'a", stored("MURANG’A", "Kenya"));
        assertEquals("Taita Taveta", stored("Taita-Taveta", "Kenya"));
        assertEquals("Lima", stored("LIMA REGION", "Peru"));
    }

    public void testNoGuessing() {
        // another word is not a spelling; an old name of it does that ("St. Elizabeth" above)
        assertNull(stored("St Elisabeth", "Jamaica"));
        // a name of another country's state
        assertNull(stored("Nimba", "Guinea"));
        assertNull(stored("Grand Est", "Germany"));
        // an old unit that leads nowhere, and a name AntWeb does not know
        assertNull(stored("Toliara", "Madagascar"));
        // a row that leads nowhere is not overridden by a valid state written alike (review of #382)
        adm1("Atsimo Andrefana", "Madagascar", false, null);
        assertNull(stored("Atsimo Andrefana", "Madagascar"));
        assertEquals("Atsimo-Andrefana", stored("ATSIMO-ANDREFANA", "Madagascar"));
        assertNull(stored("Atlantis", "Liberia"));
        assertNull(stored(null, "Liberia"));
        assertNull(stored("Nimba", null));
    }

    /** Two valid states written alike (as before the merges): the spelling is not used, the exact names still are. */
    public void testAKeyTwoValidStatesShareIsNotUsed() {
        adm1("Ombella-M'Poko", "Central African Republic", true, null);
        adm1("Ombella-Mpoko", "Central African Republic", true, null);
        assertNull(stored("OMBELLA MPOKO", "Central African Republic"));
        assertEquals("Ombella-Mpoko", stored("Ombella-Mpoko", "Central African Republic"));
        assertEquals("Ombella-M'Poko", stored("Ombella-M'Poko", "Central African Republic"));
    }

    public void testTheKey() {
        assertEquals("grand est", GeolocaleMgr.adm1SpellingKey("Grand-Est"));
        assertEquals("grand est", GeolocaleMgr.adm1SpellingKey("  GRAND   EST. "));
        assertEquals("taita taveta", GeolocaleMgr.adm1SpellingKey("Taita/Taveta"));
        assertEquals("muranga", GeolocaleMgr.adm1SpellingKey("Murang’A"));
        assertEquals("rif dimashq", GeolocaleMgr.adm1SpellingKey("Rīf Dimashq"));
        assertEquals("", GeolocaleMgr.adm1SpellingKey(null));
    }

    /** 2026-11-21 (Brian Fisher): an adm1 AntWeb recognizes is not a correction for the curator: its own group,
     *  "Recognized ... (nothing to fix)", not counted as corrected; the heading says adm1, not State. */
    public void testTheReportSectionsAreRecognizedNotCorrected() {
        for (String key : new String[] {MessageMgr.adm1NameCorrected, MessageMgr.adm1SpellingStored}) {
            assertFalse(key, MessageMgr.CORRECTED.contains(key));
            assertTrue(key, MessageMgr.RECOGNIZED_SECTIONS.contains(key));
            String heading = MessageMgr.getMessageDisplay(key);
            assertTrue(heading, heading.contains("<b>adm1 "));
            assertFalse(heading, heading.contains("State"));
            assertNull(MessageMgr.attention(key, heading));
        }
        MessageMgr m = new MessageMgr();
        m.addToMessages(MessageMgr.adm1NameCorrected, SpecimenUploadParse.countrySpellingLine("Spain: Canary Islands", "Canarias"));
        m.addToMessages(MessageMgr.adm1NameCorrected, SpecimenUploadParse.countrySpellingLine("Spain: Canary Islands", "Canarias"));
        m.addToMessages(MessageMgr.adm1SpellingStored, SpecimenUploadParse.countrySpellingLine("Cuba: Guantanamo", "Guantánamo"));
        m.addToMessages(MessageMgr.notValidAdm1, "Nowhere");     // an adm1 AntWeb does not know: still a note
        assertEquals(0, m.getCorrectedCount());
        assertEquals(3, m.getRecognizedCount());
        int notes = m.getNoteCount();
        assertEquals(1, notes);
        for (MessageMgr.ReportSection sec : m.reportSections()) {
            if (MessageMgr.RECOGNIZED_SECTIONS.contains(sec.key)) assertEquals(sec.key, MessageMgr.RECOGNIZED, sec.kind);
        }
    }
}

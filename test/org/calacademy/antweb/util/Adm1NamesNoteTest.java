package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.geolocale.Country;

/**
 * The line under a country's adm1s on its page (2026-11-21, Brian Fisher: adopt ISO 3166-2 and say so on the site):
 * where the names come from, by the country's ISO code. Madagascar and the Central African Republic keep newer units
 * than ISO lists; the countries written in another script have their adm1s in English; an island that is its own
 * country gets no line. The ENGLISH_NAMED list equals ENGLISH in tools/place/iso_states.py
 * (tests/unit/test_adm1_names_note.py).
 */
public class Adm1NamesNoteTest extends TestCase {

    public void testLatinScriptCountry() {
        assertEquals("Adm1 names follow ISO 3166-2.", Country.adm1NamesNote("FR", false));
        assertEquals("Adm1 names follow ISO 3166-2.", Country.adm1NamesNote(null, false));    // no code stored
    }

    public void testEnglishNamedCountry() {
        assertEquals("Adm1 names follow ISO 3166-2, in English.", Country.adm1NamesNote("RU", false));
        assertEquals("Adm1 names follow ISO 3166-2, in English.", Country.adm1NamesNote("IR", false));
        assertTrue(Country.ENGLISH_NAMED.contains("GR") && !Country.ENGLISH_NAMED.contains("ES"));
    }

    public void testNewerUnitsThanIso() {
        assertTrue(Country.adm1NamesNote("MG", false).contains("regions"));
        assertTrue(Country.adm1NamesNote("CF", false).contains("2020"));
        assertFalse(Country.adm1NamesNote("MG", false).contains("follow"));
    }

    public void testIslandCountry() {
        assertEquals("", Country.adm1NamesNote("US", true));
    }
}

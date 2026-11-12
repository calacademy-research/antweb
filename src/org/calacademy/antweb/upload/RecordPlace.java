package org.calacademy.antweb.upload;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;

/**
 * The country a specimen record is filed under when its file names a place that AntWeb treats as a country of its own
 * (SpecimenUploadParse.parseLine, 2026-11-11).
 *
 * Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
 * specimens never count toward the political parent. Test: would you put it in the same key as the parent?" (The
 * Nearctic project exported all ants of the United States and Hawaii had to be removed by hand.) Continental-shelf
 * islands stay adm1s. This round: Hawaii and Galapagos Islands, the places flagged as islands (geolocale.is_island);
 * they are stored as Reunion, Mayotte and Rodrigues already are: specimen.country = the island's own name. Until now
 * the upload stored the political parent (United States, Ecuador) as the country and the island only in
 * specimen.island_country, so every count of the parent included the island.
 *
 * Which records (the country is already AntWeb's valid name of what the file wrote):
 *   1. the adm1 or the country is a flagged island by its own name or by an old name of it (a not-valid country row
 *      whose valid name is the island): United States + Hawaii, Ecuador + Galapagos or Galápagos, country Hawaii,
 *      country Galapagos Islands;
 *   2. the adm1 is, in that country, an old name whose valid name is not a state of the country but a valid country:
 *      United States + Oahu (the row Oahu of United States names Hawaii), and Mauritius + Rodrigues once the row
 *      Rodrigues of Mauritius names the country Rodrigues. The target need not be flagged as an island.
 * The record is then filed under that country: its name, and (the caller) its region, subregion and bioregion. Region
 * and subregion: the place row's value where it has one, otherwise the record keeps the value it has (rowValueOr; the
 * same rule as tools/place/island_countries.py for the specimens already stored: no value is null, blank, or the
 * texts "none" and "null" the place table holds for no value; such a text is never stored). The
 * adm1 is dropped when it was the place's own name or one of its old names (aliasNames); any other adm1 is kept as
 * the file wrote it (country Hawaii + adm1 Maui: AntWeb has no adm1 rows under the islands yet, so it is stored and
 * shown on the specimen, and has no page or list of its own). Null when neither rule applies: the record is handled
 * as before.
 *
 * No database: the maps are GeolocaleMgr's (all country names of any validity; adm1 by name and country).
 * Tests: IslandCountryTest, IslandParseLineTest (parseLine itself). Docs: docs/flows/places.md ("Islands that are their own country").
 */
public final class RecordPlace {

    /** The country the record is filed under. */
    public final Country place;
    /** The adm1 to store, or null for none. */
    public final String adm1;

    private RecordPlace(Country place, String adm1) {
        this.place = place;
        this.adm1 = adm1;
    }

    /** Whether the place is flagged as an island: the record also gets specimen.island_country = its name. */
    public boolean isIsland() {
        return place.isIsland();
    }

    public static RecordPlace of(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, Collection<Adm1> adm1Rows,
                                 String country, String adm1) {
        if (countryNames == null) return null;
        Country place = validIsland(countryNames, adm1);
        if (place == null) place = validIsland(countryNames, country);
        if (place == null) place = countryOfOldAdm1(countryNames, adm1s, adm1, country);
        if (place == null || place.getName() == null) return null;
        boolean drop = adm1 == null || adm1.equals(place.getName()) || aliasNames(countryNames, adm1s, adm1Rows, place).contains(adm1);
        return new RecordPlace(place, drop ? null : adm1);
    }

    /** The valid flagged island a name means: the island itself, or the island an old name (a country row) points to. */
    public static Country validIsland(Map<String, Country> countryNames, String name) {
        if (countryNames == null || name == null) return null;
        Country row = countryNames.get(name);
        if (row == null || !row.isIsland()) return null;
        if (row.isValid()) return row;
        Country valid = noName(row.getValidName()) ? null : countryNames.get(row.getValidName());
        return valid != null && valid.isValid() && valid.isIsland() ? valid : null;
    }

    /**
     * Rule 2: the valid country that an old adm1 name of this country points to, when its valid name is not a valid
     * adm1 of the same country (then it is an ordinary old state name: GeolocaleMgr.getValidAdm1).
     */
    public static Country countryOfOldAdm1(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, String adm1, String country) {
        if (countryNames == null || adm1s == null || adm1 == null || country == null) return null;
        return countryOfOldAdm1(countryNames, adm1s, adm1s.get(adm1, country));
    }

    private static Country countryOfOldAdm1(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, Adm1 row) {
        if (row == null || row.isValid() || noName(row.getValidName())) return null;
        Adm1 sameCountry = adm1s.get(row.getValidName(), row.getParent());
        if (sameCountry != null && sameCountry.isValid()) return null;
        Country target = countryNames.get(row.getValidName());
        if (target == null || !target.isValid() || target.getName().equals(row.getParent())) return null;
        return target;
    }

    /**
     * The old names of a country: the names of the not-valid country rows whose valid name it is, and of the adm1 rows
     * that point to it by rule 2 (Hawaii: Oahu; Galapagos Islands: Galapagos, Galápagos).
     */
    public static Set<String> aliasNames(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, Collection<Adm1> adm1Rows, Country place) {
        Set<String> names = new LinkedHashSet<>();
        if (countryNames == null || place == null || place.getName() == null) return names;
        for (Country row : countryNames.values()) {
            if (row != null && !row.isValid() && place.getName().equals(row.getValidName()) && row.getName() != null) names.add(row.getName());
        }
        if (adm1s != null && adm1Rows != null) {
            for (Adm1 row : adm1Rows) {
                if (row == null || row.isValid() || !place.getName().equals(row.getValidName()) || row.getName() == null) continue;
                if (countryOfOldAdm1(countryNames, adm1s, row) == place) names.add(row.getName());
            }
        }
        return names;
    }

    /**
     * The region or subregion to store for a record filed under a place: the place row's value where it has one,
     * otherwise the value the record has already (from its country as the file wrote it). The place table holds null,
     * a blank, "none" or "null" for no value; none of them is stored. The one-time update of the stored specimens
     * (tools/place/island_countries.py, new_value) follows the same rule, so an upload and the update give a specimen
     * the same values (automatic review, 2026-11-11: the upload removed the value on null and stored the text "none").
     */
    public static String rowValueOr(String rowValue, String recordValue) {
        String value = hasNoValue(rowValue) ? recordValue : rowValue;
        return hasNoValue(value) ? null : value;
    }

    public static boolean hasNoValue(String value) {
        if (value == null) return true;
        String v = value.trim();
        return v.isEmpty() || "none".equalsIgnoreCase(v) || "null".equalsIgnoreCase(v);
    }

    /** The valid name column also holds the text "none" (and "null") for no valid name. */
    private static boolean noName(String validName) {
        if (validName == null) return true;
        String v = validName.trim();
        return v.isEmpty() || "none".equalsIgnoreCase(v) || "null".equals(v);
    }
}

package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.util.GeolocaleMgr;

/**
 * The country on whose species list the AntCat import files a name, from the type locality AntCat gives it
 * (SpeciesListUpload.importSpeciesList, the "country" column of antcat.antweb.txt; 2026-11-12).
 *
 * AntCat writes the locality as a country, often with a place in parentheses: "Ecuador (Galapagos Is)", "Hawaii
 * (Oahu)", "Indonesia (Java)". Until 2026-11-12 the import cut the text at the parenthesis and filed the name under
 * the country before it, so every name described from the Galapagos was on Ecuador's list (source AntCat). Brian
 * Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
 * specimens never count toward the political parent." The acceptance check: Ecuador's list has no Galapagos-only
 * species. Production, 2026-11-12, after the specimens were moved: 19 subspecies of Camponotus macilentus and
 * Camponotus planus with no specimen were still on Ecuador's list from AntCat (and on the Galapagos Islands list).
 *
 * Now the place in the parentheses is read as the upload reads a record's adm1, by the same rule (RecordPlace, no
 * rule of its own here): Ecuador + Galapagos is the country Galapagos Islands, United States + Hawaii or Oahu is
 * Hawaii, Mauritius + Rodrigues is Rodrigues; Mexico + Hawaii stays Mexico. The name is filed under that country
 * only. Every other locality is filed as before, under the valid country of the text before the parenthesis.
 *
 * What is its own here is only AntCat's way of writing a place: "Is" for Islands and "I." for Island, and more than
 * one place in the parentheses. placeSpellings gives the spellings RecordPlace is asked with ("Galapagos Is":
 * Galapagos Is, Galapagos Islands, Galapagos). A spelling AntCat uses that none of them covers is added to AntWeb as an
 * old name of the place (a place row that is not valid and names the country), as for the upload.
 *
 * The rows already on a parent's list: the import deletes every list row whose source is AntCat at its start, each
 * day (SpeciesListUploadDb.prepareDatabase), and files them again; a row it no longer files under the parent is gone
 * after the next daily update.
 *
 * The country before the parentheses is resolved as the import always resolved it (GeolocaleMgr.getValidCountry: the
 * name letter for letter, an old name through its valid name; no other capitals or accents). The import asks filing()
 * with that country; listCountry() does the same lookup on a given map, for the tests.
 *
 * A missing old name is seen in the import's report (2026-11-12, review of #347): when the country before the
 * parentheses has places that are countries of their own (United States: Hawaii; Ecuador: Galapagos Islands;
 * Mauritius: Rodrigues) and the place in the parentheses is neither one of them nor a state AntWeb knows in that
 * country, the report lists the locality ("United States (Hawaiian Is): filed under United States"), once, and the
 * import's compute.log line counts them. Such a place is either no island at all or a spelling to add as an old name.
 *
 * No database: the maps are GeolocaleMgr's. Tests: AntcatLocalityTest, AntcatIslandRowsDbTest. Docs:
 * docs/flows/antcat-to-antweb.md, docs/flows/places.md ("Islands that are their own country").
 */
public final class AntcatLocality {

    private AntcatLocality() {
    }

    /** Written to compute.log after a successful daily AntCat import ("AntCat import done"); tests/e2e/numbers_sample.py reads it. */
    public static final String ISLAND_LOG = "names of an island country filed under the island only (2026-11-12)";

    /** The text before the first parenthesis, trimmed: the country AntCat names ("Ecuador (Galapagos Is)": Ecuador). */
    public static String countryPart(String locality) {
        if (locality == null) return null;
        int open = locality.indexOf('(');
        return (open < 0 ? locality : locality.substring(0, open)).trim();
    }

    /** The text inside the first parentheses, trimmed ("Ecuador (Galapagos Is)": Galapagos Is), or null. */
    public static String placePart(String locality) {
        if (locality == null) return null;
        int open = locality.indexOf('(');
        if (open < 0) return null;
        int close = locality.indexOf(')', open);
        String place = (close < 0 ? locality.substring(open + 1) : locality.substring(open + 1, close)).trim();
        return place.isEmpty() ? null : place;
    }

    /**
     * The spellings to ask RecordPlace with for the place in the parentheses, the text as written first: the whole
     * text and, when it lists more than one place (comma, semicolon or colon), the first; for each, AntCat's "Is"
     * (Islands) and "I." (Island) written out and left off, and a written-out Island or Islands left off.
     */
    public static List<String> placeSpellings(String place) {
        Set<String> spellings = new LinkedHashSet<>();
        if (place == null || place.trim().isEmpty()) return new ArrayList<>(spellings);
        List<String> texts = new ArrayList<>();
        texts.add(place.trim());
        String first = place.split("[,;:]")[0].trim();
        if (!first.isEmpty() && !first.equals(place.trim())) texts.add(first);
        for (String text : texts) {
            spellings.add(text);
            String[][] endings = {{" Is.", " Islands"}, {" Is", " Islands"}, {" I.", " Island"}, {" I", " Island"},
                                  {" Islands", null}, {" Island", null}};
            for (String[] ending : endings) {
                if (text.endsWith(ending[0]) && text.length() > ending[0].length()) {
                    String base = text.substring(0, text.length() - ending[0].length()).trim();
                    if (ending[1] != null) spellings.add(base + ending[1]);
                    if (!base.isEmpty()) spellings.add(base);
                    break;
                }
            }
        }
        return new ArrayList<>(spellings);
    }

    /** Where a name is filed, and what the import's report says about its locality. */
    public static final class Filing {
        /** The valid country of the text before the parentheses, as the import always resolved it. */
        public final Country before;
        /** The country on whose list the name is filed: the country of its own the parentheses name, or else before. */
        public final Country country;
        /** The locality as the report lists it when its place is not known (placeNotKnownLine), or null. */
        public final String note;

        Filing(Country before, Country country, String note) {
            this.before = before;
            this.country = country;
            this.note = note;
        }
    }

    /**
     * Where a name with this AntCat locality is filed, given the valid country of the text before the parentheses
     * (the import: GeolocaleMgr.getValidCountry, as before 2026-11-12): under the country the place in the parentheses
     * is, by the upload's rule (RecordPlace), or else under that country. Null when there is no such country (the
     * import reports "Country not found", as before), whatever the parentheses say.
     *
     * parents: the countries that have places which are countries of their own (parentsOfOwnCountries), or null for no
     * notes. For such a country a place in the parentheses that is neither one of those places nor a state AntWeb
     * knows in the country gets a note: the name is filed under the country, and the place may be an island's name
     * AntWeb does not have yet.
     */
    public static Filing filing(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, Collection<Adm1> adm1Rows,
                                Set<String> parents, Country before, String locality) {
        if (before == null || before.getName() == null) return null;
        List<String> spellings = placeSpellings(placePart(locality));
        for (String place : spellings) {
            RecordPlace own = RecordPlace.of(countryNames, adm1s, adm1Rows, before.getName(), place);
            if (own != null) return new Filing(before, own.place, null);
        }
        String note = null;
        if (parents != null && parents.contains(before.getName()) && !spellings.isEmpty()) {
            boolean knownState = false;
            for (String place : spellings) {
                if (GeolocaleMgr.validAdm1(adm1s, place, before.getName()) != null) knownState = true;
            }
            if (!knownState) note = placeNotKnownLine(locality, before.getName());
        }
        return new Filing(before, before, note);
    }

    /**
     * The valid country on whose list a name with this AntCat locality is filed, on a given map of country names (the
     * tests): filing() with the valid country of the text before the parentheses, looked up as the import does
     * (GeolocaleMgr.getValidCountry is validCountry on the manager's map). Null when AntWeb has no such country.
     */
    public static Country listCountry(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, Collection<Adm1> adm1Rows,
                                      String locality) {
        Filing filing = filing(countryNames, adm1s, adm1Rows, null, GeolocaleMgr.validCountry(countryNames, countryPart(locality)), locality);
        return filing == null ? null : filing.country;
    }

    /**
     * The names of the valid countries that have places which are countries of their own: the political parent of a
     * valid place flagged as an island (geolocale.country of its row: United States for Hawaii, Ecuador for Galapagos
     * Islands), and a country one of whose old state names is a valid country (RecordPlace's second rule: Mauritius
     * for Rodrigues, United States for Oahu). Only their localities get a note (the report would otherwise list every
     * place AntWeb has no row for).
     */
    public static Set<String> parentsOfOwnCountries(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, Collection<Adm1> adm1Rows) {
        Set<String> parents = new TreeSet<>();
        if (countryNames == null) return parents;
        for (Country island : countryNames.values()) {
            if (island == null || !island.isIsland() || !island.isValid() || RecordPlace.hasNoValue(island.getCountry())) continue;
            if (island.getCountry().equals(island.getName())) continue;
            Country parent = countryNames.get(island.getCountry());
            if (parent != null && parent.isValid()) parents.add(parent.getName());
        }
        if (adm1s != null && adm1Rows != null) {
            for (Adm1 row : adm1Rows) {
                if (row == null || row.getName() == null || row.getParent() == null) continue;
                if (RecordPlace.countryOfOldAdm1(countryNames, adm1s, row.getName(), row.getParent()) != null) parents.add(row.getParent());
            }
        }
        return parents;
    }

    /** The report's line for a locality whose place is not known: "United States (Hawaiian Is): filed under United States". */
    public static String placeNotKnownLine(String locality, String country) {
        return (locality == null ? "" : locality.trim()) + ": filed under " + country;
    }

    /** The part of the import's compute.log line that counts those localities (0 too, so the line always says). */
    public static String placesNotKnownLog(int localities) {
        return "localities of a country with island countries whose place in parentheses is not a known name: " + localities;
    }
}

package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * The valid country on whose list a name with this AntCat locality is filed: the country the place in the
     * parentheses is, by the upload's rule (RecordPlace), or else the valid country of the text before them. Null when
     * AntWeb has no valid country for that text (the import reports "Country not found", as before), whatever the
     * parentheses say.
     */
    public static Country listCountry(Map<String, Country> countryNames, MultiKeyMap<String, Adm1> adm1s, Collection<Adm1> adm1Rows,
                                      String locality) {
        String countryPart = countryPart(locality);
        if (countryPart == null || countryPart.isEmpty()) return null;
        Country country = GeolocaleMgr.validCountry(countryNames, countryPart);
        if (country == null) return null;
        for (String place : placeSpellings(placePart(locality))) {
            RecordPlace own = RecordPlace.of(countryNames, adm1s, adm1Rows, country.getName(), place);
            if (own != null) return own.place;
        }
        return country;
    }
}

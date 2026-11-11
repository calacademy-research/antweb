package org.calacademy.antweb.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

/**
 * An old place name in a link goes to the same address with the current name (2026-11-10, Brian Fisher: AntWeb takes
 * the current country names, and links from outside keep the old ones). AntWeb keeps an old name as a not-valid place
 * whose valid name is the current place (USA: United States; after a rename, Ivory Coast: Côte d'Ivoire). Until now
 * country.do?name=USA opened the empty page of the not-valid place ("This location is Invalid"), a list with
 * countryName=USA was empty and adm1.do?name=..&country=USA said the adm1 was not found.
 *
 * Every page request (GET or HEAD of a .do address, SessionRequestFilter) is looked at before its page runs:
 *   country  the parameters countryName and country, and name on country.do: an old country name with a valid name
 *            becomes that country's name;
 *   adm1     the parameter adm1Name, and name on adm1.do, with the country given by countryName or country: an old
 *            adm1 name with a valid name in that country becomes that adm1's name.
 * When a name changes, the answer is a permanent redirect (301) to the same address with the new names URL-encoded
 * (UTF-8: Côte d'Ivoire is C%C3%B4te+d%27Ivoire) and every other parameter as it was, so bookmarks and search
 * engines move. A valid name, an unknown name, and an old name without a valid name are left as they are. Not
 * changed: the search parameter adm1= (a specimen keeps the state as its file wrote it), posts, and the tools that
 * work on the old-name rows themselves (SKIP). Which name is current: GeolocaleMgr.currentCountryName,
 * currentAdm1Name. Docs: docs/flows/places.md. Tests: OldPlaceNamesTest, tests/e2e/test_old_place_names.py.
 */
public final class OldPlaceNames {

    private OldPlaceNames() {}

    /** The current name of a place named by an old name; null when the name is not an old name with a valid name. */
    public interface Names {
        String country(String name);
        String adm1(String adm1Name, String countryName);
    }

    /** AntWeb's places in memory. */
    public static final Names PLACES = new Names() {
        public String country(String name) { return GeolocaleMgr.currentCountryName(name); }
        public String adm1(String adm1Name, String countryName) { return GeolocaleMgr.currentAdm1Name(adm1Name, countryName); }
    };

    /** The administrators' tools for the place rows themselves: an old name there means the old-name row. */
    static final Set<String> SKIP = Set.of("/editGeolocale.do", "/geolocaleMgr.do", "/adm1Mgr.do");

    /** The address (path and query) this request moved to, or null when no place name in it is an old name. */
    public static String movedTo(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) return null;
        if (!AntwebMgr.isPopulated()) return null;      // the places are not loaded yet
        String query = movedQuery(request.getServletPath(), request.getQueryString(), PLACES);
        return query == null ? null : request.getRequestURI() + "?" + query;
    }

    /**
     * The query string with each old place name replaced by its current name (URL-encoded; every other parameter
     * exactly as it was written), or null when nothing changes.
     */
    public static String movedQuery(String path, String rawQuery, Names names) {
        if (path == null || rawQuery == null || rawQuery.isEmpty() || SKIP.contains(path)) return null;
        List<String[]> raw = new ArrayList<>();      // {key, value or null} as written
        List<String[]> read = new ArrayList<>();     // the same, decoded
        for (String pair : rawQuery.split("&", -1)) {
            int eq = pair.indexOf('=');
            String[] written = eq < 0 ? new String[] {pair, null} : new String[] {pair.substring(0, eq), pair.substring(eq + 1)};
            String key = decode(written[0]);
            String value = written[1] == null ? null : decode(written[1]);
            if (key == null || (written[1] != null && value == null)) return null;      // not a query we can read: left alone
            raw.add(written);
            read.add(new String[] {key, value});
        }
        boolean changed = false;
        for (int i = 0; i < read.size(); i++) {
            String key = read.get(i)[0];
            boolean isCountry = "countryName".equals(key) || "country".equals(key) || ("name".equals(key) && "/country.do".equals(path));
            if (!isCountry || read.get(i)[1] == null) continue;
            String current = names.country(read.get(i)[1]);
            if (current != null && !current.equals(read.get(i)[1])) {
                read.get(i)[1] = current;
                raw.get(i)[1] = HttpUtil.encode(current);
                changed = true;
            }
        }
        int adm1 = first(read, "adm1Name");
        if (adm1 < 0 && "/adm1.do".equals(path)) adm1 = first(read, "name");
        int country = first(read, "countryName");
        if (country < 0) country = first(read, "country");
        if (adm1 >= 0 && country >= 0 && read.get(adm1)[1] != null && read.get(country)[1] != null) {
            String current = names.adm1(read.get(adm1)[1], read.get(country)[1]);
            if (current != null && !current.equals(read.get(adm1)[1])) {
                raw.get(adm1)[1] = HttpUtil.encode(current);
                changed = true;
            }
        }
        if (!changed) return null;
        StringBuilder query = new StringBuilder();
        for (String[] pair : raw) {
            if (query.length() > 0) query.append('&');
            query.append(pair[0]);
            if (pair[1] != null) query.append('=').append(pair[1]);
        }
        return query.toString();
    }

    private static int first(List<String[]> pairs, String key) {
        for (int i = 0; i < pairs.size(); i++) if (key.equals(pairs.get(i)[0])) return i;
        return -1;
    }

    /** A query-string component decoded as UTF-8; null when it is not valid percent-encoding. */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

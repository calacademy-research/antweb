package org.calacademy.antweb.util;

import java.util.List;

import org.calacademy.antweb.Login;
import org.calacademy.antweb.SpeciesListable;

/**
 * Which species lists (projects, countries, adm1s) a login may change.
 *
 * Administrators may change any list. A curator may change only the lists assigned to them
 * (login_project and login_country, loaded into Login.getProjects() / getGeolocales() at login).
 * A list is named by its key: the project name for a project, the geolocale id for a geolocale;
 * the geolocale's name is also accepted.
 */
public final class SpeciesListAccess {

    private SpeciesListAccess() {}

    public static boolean canEdit(Login login, String speciesListKey) {
        return canEdit(login, speciesListKey, name -> {
            org.calacademy.antweb.geolocale.Geolocale g = GeolocaleMgr.getGeolocale(name);
            return g == null ? null : g.getKey();
        });
    }

    /**
     * As canEdit(login, key); keyOfName gives the key a name stands for (GeolocaleMgr), or null. A name is accepted
     * only when it stands for this list, or for no place at all (2026-11-02): a curator assigned the adm1 "Georgia" could edit
     * the country Georgia, and one assigned Mexico's "Distrito Federal" Brazil's, because names repeat.
     */
    public static boolean canEdit(Login login, String speciesListKey, java.util.function.Function<String, String> keyOfName) {
        if (login == null) return false;
        if (login.isAdmin()) return true;
        if (speciesListKey == null || speciesListKey.isEmpty()) return false;
        List<SpeciesListable> lists = login.getSpeciesListList();
        if (lists == null) return false;
        String keyOfThisName = null;
        boolean resolved = false;
        for (SpeciesListable list : lists) {
            if (list == null) continue;
            if (speciesListKey.equals(list.getKey())) return true;
            if (speciesListKey.equals(list.getName())) {
                if (speciesListKey.equals(list.getKey())) return true;
                if (!resolved) {
                    keyOfThisName = keyOfName == null ? null : keyOfName.apply(speciesListKey);
                    resolved = true;
                }
                // A name that resolves to no list (places not loaded yet) is matched by name, as before.
                if (keyOfThisName == null || keyOfThisName.equals(list.getKey())) return true;
            }
        }
        return false;
    }

    /** True when the name is empty or "none"/"null", i.e. no list is selected. */
    public static boolean isNone(String speciesListKey) {
        return speciesListKey == null || speciesListKey.isEmpty()
            || "none".equals(speciesListKey) || "null".equals(speciesListKey);
    }

    public static final String NOT_ASSIGNED =
        "You can change only the projects and regions assigned to you. Ask an AntWeb administrator to assign it.";
}

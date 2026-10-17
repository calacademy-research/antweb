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
        if (login == null) return false;
        if (login.isAdmin()) return true;
        if (speciesListKey == null || speciesListKey.isEmpty()) return false;
        List<SpeciesListable> lists = login.getSpeciesListList();
        if (lists == null) return false;
        for (SpeciesListable list : lists) {
            if (list == null) continue;
            if (speciesListKey.equals(list.getKey()) || speciesListKey.equals(list.getName())) return true;
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

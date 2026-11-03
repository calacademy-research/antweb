package org.calacademy.antweb.util;

import java.util.List;

import org.calacademy.antweb.Login;
import org.calacademy.antweb.SpeciesListable;
import org.calacademy.antweb.home.CuratorTaxonSetDb;

/**
 * Which species lists (projects, countries, adm1s) a login may change, and which projects it may edit.
 *
 * Species lists (Brian Fisher, 2026-11-02, "encourage participation"): every curator and administrator may edit any
 * place's or project's species list with the Species List Tool (canEdit); a login that is not a curator may edit none.
 * The lists built by the system (CuratorTaxonSetDb.SYSTEM_PROJECTS: worldants, allantwebants) stay for administrators.
 * Every change is recorded with the curator (geolocale_taxon_log, proj_taxon_log, and the disputes with curator_id).
 * Until 2026-11-02 a curator could change only the lists assigned to them (login_project and login_country).
 *
 * Projects (EditProjectAction: the project's title, page text and settings): administrators any project, a curator only
 * a project assigned to them (canEditProject, the old rule). A list is named by its key: the project name for a
 * project, the geolocale id for a geolocale; the geolocale's name is also accepted.
 * Tests: test.org.calacademy.antweb.security.SecurityBatch5Test.
 */
public final class SpeciesListAccess {

    private SpeciesListAccess() {}

    /** Whether the login may change the species list: any curator or administrator, any list but the system lists. */
    public static boolean canEdit(Login login, String speciesListKey) {
        if (login == null) return false;
        if (login.isAdmin()) return true;
        if (!login.isCurator()) return false;
        if (speciesListKey == null || speciesListKey.isEmpty()) return false;
        return !CuratorTaxonSetDb.SYSTEM_PROJECTS.contains(speciesListKey);
    }

    /** Whether the login may edit the project itself (EditProjectAction): administrators, or a curator it is assigned to. */
    public static boolean canEditProject(Login login, String projectName) {
        if (login == null) return false;
        if (login.isAdmin()) return true;
        if (projectName == null || projectName.isEmpty()) return false;
        List<SpeciesListable> lists = login.getSpeciesListList();
        if (lists == null) return false;
        for (SpeciesListable list : lists) {
            if (list == null) continue;
            if (projectName.equals(list.getKey()) || projectName.equals(list.getName())) return true;
        }
        return false;
    }

    /** True when the name is empty or "none"/"null", i.e. no list is selected. */
    public static boolean isNone(String speciesListKey) {
        return speciesListKey == null || speciesListKey.isEmpty()
            || "none".equals(speciesListKey) || "null".equals(speciesListKey);
    }

    /** The message when a login may not change a species list. */
    public static final String NOT_ASSIGNED =
        "Only curators may change species lists (any place's or project's list). The AntCat list and the All AntWeb list"
        + " are built by AntWeb; ask an AntWeb administrator.";

    /** The message when a curator may not edit a project (EditProjectAction). */
    public static final String PROJECT_NOT_ASSIGNED =
        "You can edit only the projects assigned to you. Ask an AntWeb administrator to assign it.";

    /** The Species List Tool's sentence on who may edit which list. */
    public static final String WHO_MAY_EDIT =
        "Any curator may edit any place's or project's species list: open the place's species list and click Edit Species"
        + " List. Every change is recorded with your name in the list's history.";
}

package org.calacademy.antweb.curate.login;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.calacademy.antweb.Project;
import org.calacademy.antweb.curate.login.LoginEdit.Choice;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.util.GeolocaleMgr;
import org.calacademy.antweb.util.ProjectMgr;

/**
 * The lists of the login form, from AntWeb's in-memory caches: live projects and valid
 * countries that can be assigned, and the title of every project.
 */
public final class LoginEditOptions {

    private LoginEditOptions() {}

    private static boolean isAggregate(String name) {
        return Project.WORLDANTS.equals(name) || Project.ALLANTWEBANTS.equals(name);
    }

    private static String label(Project p) {
        String title = p.getTitle();
        return (title == null || title.isEmpty() ? p.getName() : title) + " (" + p.getName() + ")";
    }

    /** Live projects (not the two "all ants" lists), sorted by title. */
    public static List<Choice> projectOptions() {
        List<Choice> out = new ArrayList<>();
        List<Project> live = ProjectMgr.getLiveProjects();
        if (live == null) return out;
        for (Project p : live) {
            if (p == null || p.getName() == null || isAggregate(p.getName())) continue;
            out.add(new Choice(p.getName(), label(p)));
        }
        out.sort(Comparator.comparing((Choice c) -> c.label.toLowerCase()));
        return out;
    }

    /** Every project's label by name (for assignments to projects that are no longer live). */
    public static Map<String, String> projectLabels() {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Project> all = ProjectMgr.getAllProjectsHash();
        if (all == null) return out;
        for (Project p : all.values()) {
            if (p != null && p.getName() != null) out.put(p.getName(), label(p));
        }
        return out;
    }

    /** Valid countries, by name. */
    public static List<Choice> countryOptions() {
        List<Choice> out = new ArrayList<>();
        List<Geolocale> countries = GeolocaleMgr.getValidCountries();
        if (countries == null) return out;
        for (Geolocale g : countries) {
            if (g != null && g.getName() != null) out.add(new Choice(g.getName(), g.getName()));
        }
        out.sort(Comparator.comparing((Choice c) -> c.label.toLowerCase()));
        return out;
    }

    /** Read the login's data for the form and keep it in the session for viewLogin.jsp. */
    public static LoginEditPage.PageData prepare(Connection c, HttpServletRequest request, int id, boolean isNew) throws SQLException {
        LoginEditPage.PageData d = LoginEditPage.read(c, id, isNew, projectOptions(), countryOptions(), projectLabels());
        request.getSession().setAttribute(LoginEditPage.SESSION_ATTR, d);
        return d;
    }
}

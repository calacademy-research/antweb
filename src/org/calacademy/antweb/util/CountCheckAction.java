package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.geolocale.Georank;

/**
 * countCheck.do (administrators, read-only): CountCheck for a sample of places. Default sample:
 * Madagascar, Reunion, the country with the most morphospecies specimens (after those two), the
 * live curated project with the most species (only live projects are recounted each night), the Malagasy bioregion and the CASC museum. Parameters
 * geolocaleId (one or more), project, museum and bioregion replace the sample.
 * The page ends with a line "Real mismatches: N. Pending: M." read by tests/e2e/test_count_check.py.
 */
public final class CountCheckAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(CountCheckAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;
        ActionForward c = Check.admin(request, mapping); if (c != null) return c;

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("CountCheckAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            connection.setReadOnly(true);
            List<String> notes = new ArrayList<>();
            List<CountCheck.Place> places = places(request, connection, notes);
            CountCheck.Result result = new CountCheck(connection).run(places, CountCheck.lastCompletedRun(computeLog()));
            result.notes.addAll(notes);
            request.setAttribute("html", html(result, AntwebProps.getDomainApp()));
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            request.setAttribute("message", "The count check failed: " + Html.esc(e.getMessage()));
            return mapping.findForward("message");
        } finally {
            try { if (connection != null) connection.setReadOnly(false); } catch (SQLException e) { /* closing */ }
            DBUtil.close(connection, this, dbMethodName);
        }
        return mapping.findForward("success");
    }

    /** The lines of web/log/compute.log (empty if it cannot be read). */
    static List<String> computeLog() {
        try {
            return java.nio.file.Files.readAllLines(java.nio.file.Paths.get(LogMgr.getWebLogRoot() + "compute.log"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            s_log.warn("computeLog() e:" + e);
            return new ArrayList<>();
        }
    }

    static CountCheck.Place geolocalePlace(Geolocale g) {
        String column = "country";
        if (g.isIsland()) column = "island_country";
        else if (Georank.ADM1.equals(g.getGeorank())) column = "adm1";
        else if (!Georank.COUNTRY.equals(g.getGeorank())) return null;   // regions and subregions have no specimen column
        return new CountCheck.Place(CountCheck.GEOLOCALE, "" + g.getId(), g.getName(), column, g.getName());
    }

    static List<CountCheck.Place> places(HttpServletRequest request, Connection connection, List<String> notes) throws SQLException {
        List<CountCheck.Place> places = new ArrayList<>();
        String[] geolocaleIds = request.getParameterValues("geolocaleId");
        String project = request.getParameter("project");
        String museum = request.getParameter("museum");
        String bioregion = request.getParameter("bioregion");
        boolean custom = geolocaleIds != null || project != null || museum != null || bioregion != null;

        List<Geolocale> geolocales = new ArrayList<>();
        if (custom) {
            if (geolocaleIds != null) {
                for (String id : geolocaleIds) {
                    try {
                        Geolocale g = GeolocaleMgr.getGeolocale(Integer.parseInt(id.trim()));
                        if (g != null) geolocales.add(g); else notes.add("No geolocale " + Html.esc(id));
                    } catch (NumberFormatException e) {
                        notes.add("Not a geolocale id: " + Html.esc(id));
                    }
                }
            }
        } else {
            for (String name : new String[] {"Madagascar", "Reunion"}) {
                Geolocale g = GeolocaleMgr.getValidCountry(name);
                if (g != null) geolocales.add(g); else notes.add("Country " + name + " not found");
            }
            String morphoCountry = null;
            String query = "select country, count(*) n from specimen where status = 'morphotaxon' and country is not null"
                + " and country not in ('Madagascar', 'Reunion') group by country order by n desc limit 1";
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(query)) {
                if (r.next()) morphoCountry = r.getString(1);
            }
            if (morphoCountry != null) {
                Geolocale g = GeolocaleMgr.getValidCountry(morphoCountry);
                if (g != null) geolocales.add(g); else notes.add("Country " + Html.esc(morphoCountry) + " not found");
            }
            // The nightly run recounts only live projects (ProjTaxonCountDb.childrenCountCrawl, ProjectMgr.getLiveProjects):
            // the largest live curated project.
            java.util.Set<String> live = new java.util.HashSet<>();
            for (Project p : ProjectMgr.getLiveProjects()) live.add(p.getName());
            query = "select project_name, count(*) n from proj_taxon where project_name not in ('worldants', 'allantwebants', 'fossilants')"
                + " group by project_name order by n desc";
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(query)) {
                while (r.next()) {
                    if (live.contains(r.getString(1))) {
                        project = r.getString(1);
                        break;
                    }
                }
            }
            museum = "CASC";
            bioregion = "Malagasy";
        }
        for (Geolocale g : geolocales) {
            CountCheck.Place p = geolocalePlace(g);
            if (p != null) places.add(p); else notes.add(Html.esc(g.getName()) + ": only countries, adm1s and islands can be checked");
        }
        if (project != null) {
            Project p = ProjectMgr.getProject(project, false);
            if (p != null) places.add(new CountCheck.Place(CountCheck.PROJECT, p.getName(), "project " + p.getName(), null, null));
            else notes.add("No project " + Html.esc(project));
        }
        if (museum != null) {
            Museum m = MuseumMgr.getMuseum(museum);
            if (m != null) places.add(new CountCheck.Place(CountCheck.MUSEUM, m.getCode(), "museum " + m.getCode(), "museum", m.getCode()));
            else notes.add("No museum " + Html.esc(museum));
        }
        if (bioregion != null) {
            Bioregion b = BioregionMgr.getBioregion(bioregion);
            if (b != null) places.add(new CountCheck.Place(CountCheck.BIOREGION, b.getName(), b.getName() + " bioregion", "bioregion", b.getName()));
            else notes.add("No bioregion " + Html.esc(bioregion));
        }
        return places;
    }

    // Plain wording (Brian Fisher, 2026-10-20: "Count check is not clear"): what is checked, what a
    // difference means, what to do.
    public static final String HELP = ReportHelp.box(
        "AntWeb does not count specimens when a page is shown: the nightly run (05:00 to about 08:15 Pacific) "
            + "counts them and stores the numbers, and the pages show the stored numbers. This page counts again, "
            + "straight from the specimen records, for a few sample places (Madagascar, Reunion, the country with the most "
            + "morphospecies, the largest live curated project, the Malagasy bioregion and the CASC museum), and compares. "
            + "Checked: the specimens of each species, the species of each genus and the genera of each subfamily in the "
            + "place's taxon list, the \"Global\" specimens of each species, and the place's image numbers (Images, "
            + "images of each view, Imaged specimens). Every number that differs is listed with the number the site "
            + "shows (\"Stored\") and the fresh count (\"Recount\").",
        "A difference means the site shows a wrong number on that place's page. A \"pending\" difference is expected: "
            + "something changed after the last nightly run (an upload, a name update, a list edit) and the next nightly "
            + "run will count it. A \"real\" difference has no such reason: the nightly count missed something.",
        "Usually nothing: pending differences go away after the next nightly run. If a real difference is still listed "
            + "the next day, after the nightly run has finished, email the list (place, number, taxon, both values) "
            + "to antweb@antweb.org. Between 05:00 and 08:15 Pacific the nightly run is rewriting the numbers, so "
            + "differences are expected then; look again later.");

    static String html(CountCheck.Result result, String domainApp) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringBuilder b = new StringBuilder(HELP);
        if (result.inNightlyWindow) {
            b.append("<p><b>The nightly run is rebuilding the counts now (05:00 to 08:15 Pacific); differences are expected until it ends.</b></p>");
        }
        if (result.lastRunEnd == null) {
            b.append("<p><b>compute.log has no completed nightly run</b> (it may have been emptied, or the run failed or was stopped). "
                + "The stored counts may then be older than one day, and differences may only mean that the counts were not rebuilt. "
                + "Changes after ").append(f.format(result.countedUntil)).append(" are treated as pending.</p>");
        } else {
            b.append("<p>Counts written by the nightly run that ended ").append(f.format(result.lastRunEnd)).append(".</p>");
        }
        b.append("<p>").append(result.uploadSinceNightly ? "Specimens were uploaded after that." : "No specimen upload after that.").append("</p>");
        for (String note : result.notes) b.append("<p>Note: ").append(note).append("</p>");
        b.append("<table border=1 cellpadding=4 style=\"border-collapse:collapse\"><tr><th>Place</th><th>Rows checked</th><th>Real</th><th>Pending</th></tr>");
        for (CountCheck.Place p : result.places) {
            int real = 0, pending = 0;
            for (CountCheck.Mismatch m : result.mismatches) {
                if (m.place != p) continue;
                if (m.isReal()) ++real; else ++pending;
            }
            b.append("<tr><td>").append(Html.esc(p.label)).append("</td><td>").append(result.rowsChecked.getOrDefault(p, 0))
             .append("</td><td>").append(real).append("</td><td>").append(pending).append("</td></tr>");
        }
        b.append("</table>");
        for (boolean real : new boolean[] {true, false}) {
            List<CountCheck.Mismatch> list = new ArrayList<>();
            for (CountCheck.Mismatch m : result.mismatches) if (m.isReal() == real) list.add(m);
            b.append("<h3>").append(real ? "Real differences" : "Pending differences").append(" (").append(list.size()).append(")</h3>");
            if (list.isEmpty()) {
                b.append("<p>None.</p>");
                continue;
            }
            b.append("<table border=1 cellpadding=4 style=\"border-collapse:collapse\"><tr><th>Place</th><th>Number</th><th>Taxon</th><th>Stored (shown)</th><th>Recount</th>")
             .append(real ? "" : "<th>Why pending</th>").append("</tr>");
            int n = 0;
            for (CountCheck.Mismatch m : list) {
                if (++n > 2000) {
                    b.append("<tr><td colspan=6>... ").append(list.size() - 2000).append(" more</td></tr>");
                    break;
                }
                b.append("<tr class=\"count-mismatch\"><td>").append(Html.esc(m.place.label)).append("</td><td>").append(m.check)
                 .append("</td><td>").append(m.check.startsWith("place ") ? Html.esc(m.taxonName) : Taxon.makeDescriptionLink(m.taxonName)).append("</td><td>").append(m.stored)
                 .append("</td><td>").append(m.recount).append("</td>");
                if (!real) b.append("<td>").append(Html.esc(m.pendingReason)).append("</td>");
                b.append("</tr>");
            }
            b.append("</table>");
        }
        b.append("<p id=\"count-check-result\" data-real=\"").append(result.real()).append("\" data-pending=\"").append(result.pending())
         .append("\" data-window=\"").append(result.inNightlyWindow ? 1 : 0)
         .append("\" data-nightly=\"").append(result.lastRunEnd == null ? 0 : 1).append("\">Real mismatches: ").append(result.real())
         .append(". Pending: ").append(result.pending()).append(".</p>");
        return b.toString();
    }
}

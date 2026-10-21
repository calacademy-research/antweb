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
 * subregion of Madagascar (since 2026-10-20: counted from its countries), the
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
            boolean custom = isCustom(request);
            List<CountCheck.Place> rotating = custom ? new ArrayList<>() : rotatingSample(connection, java.time.LocalDate.now().getDayOfYear());
            int seconds = ROTATING_SECONDS;
            try {
                if (request.getParameter("seconds") != null) seconds = Math.max(0, Math.min(600, Integer.parseInt(request.getParameter("seconds"))));
            } catch (NumberFormatException e) { /* default */ }
            boolean invariants = !custom || "1".equals(request.getParameter("invariants"));
            List<String> log = computeLog();
            CountCheck.Result result = new CountCheck(connection).run(places, rotating, seconds, CountCheck.lastCompletedRun(log),
                CountCheck.regionRuleCounted(log), CountInvariants.rulesCounted(log), invariants, TaxonMgr.getValidSpeciesCount());
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

    /**
     * The rotating sample is recounted until the whole check (invariants, core places, rotating places) has run
     * this many seconds, so the page answers within about a minute (Cloudflare ends requests after 100 s).
     * Parameter seconds (0 to 600) changes it.
     */
    static final int ROTATING_SECONDS = 50;

    static boolean isCustom(HttpServletRequest request) {
        return request.getParameterValues("geolocaleId") != null || request.getParameter("project") != null
            || request.getParameter("museum") != null || request.getParameter("bioregion") != null;
    }

    /**
     * Today's slice of every kind of place (2026-10-21), so every place is recounted from the specimen records
     * within a cycle: countries 1/7 a day (all in a week), live adm1s 1/30 (all in a month), subregions and
     * regions 1/7, live projects 1/7, museums 1/7, one bioregion a day. day: day of the year.
     */
    static List<CountCheck.Place> rotatingSample(Connection connection, int day) {
        List<CountCheck.Place> places = new ArrayList<>();
        List<Geolocale> countries = sorted(GeolocaleMgr.getValidCountries());
        for (int i = 0; i < countries.size(); i++) if (i % 7 == day % 7) add(places, geolocalePlace(countries.get(i)));
        List<Geolocale> adm1s = new ArrayList<>();
        for (Geolocale g : sorted(GeolocaleMgr.getValidAdm1s())) if (g.isLive()) adm1s.add(g);
        for (int i = 0; i < adm1s.size(); i++) if (i % 30 == day % 30) add(places, geolocalePlace(adm1s.get(i)));
        List<Geolocale> regions = new ArrayList<>();
        regions.addAll(sorted(GeolocaleMgr.getGeolocales(Georank.SUBREGION)));
        regions.addAll(sorted(GeolocaleMgr.getGeolocales(Georank.REGION)));
        regions = sorted(regions);
        for (int i = 0; i < regions.size(); i++) if (i % 7 == day % 7) add(places, geolocalePlace(regions.get(i)));
        List<String> projects = new ArrayList<>();
        for (Project p : ProjectMgr.getLiveProjects()) {
            if (!Project.WORLDANTS.equals(p.getName()) && !Project.ALLANTWEBANTS.equals(p.getName())) projects.add(p.getName());
        }
        java.util.Collections.sort(projects);
        for (int i = 0; i < projects.size(); i++) {
            if (i % 7 == day % 7) places.add(new CountCheck.Place(CountCheck.PROJECT, projects.get(i), "project " + projects.get(i), null, null));
        }
        List<String> museums = new ArrayList<>();
        if (MuseumMgr.getMuseums() != null) for (Museum m : MuseumMgr.getMuseums()) museums.add(m.getCode());
        java.util.Collections.sort(museums);
        for (int i = 0; i < museums.size(); i++) {
            if (i % 7 == day % 7) places.add(new CountCheck.Place(CountCheck.MUSEUM, museums.get(i), "museum " + museums.get(i), "museum", museums.get(i)));
        }
        List<String> bioregions = new ArrayList<>();
        if (BioregionMgr.getBioregions() != null) for (Bioregion b : BioregionMgr.getBioregions()) bioregions.add(b.getName());
        java.util.Collections.sort(bioregions);
        if (!bioregions.isEmpty()) {
            String b = bioregions.get(day % bioregions.size());
            places.add(new CountCheck.Place(CountCheck.BIOREGION, b, b + " bioregion", "bioregion", b));
        }
        return places;
    }

    private static List<Geolocale> sorted(List<Geolocale> list) {
        List<Geolocale> l = list == null ? new ArrayList<>() : new ArrayList<>(list);
        l.sort(java.util.Comparator.comparingInt(Geolocale::getId));
        return l;
    }

    private static void add(List<CountCheck.Place> places, CountCheck.Place p) {
        if (p != null) places.add(p);
    }

    static CountCheck.Place geolocalePlace(Geolocale g) {
        String column = "country";
        if (g.isIsland()) column = "island_country";
        else if (Georank.ADM1.equals(g.getGeorank())) column = "adm1";
        else if (g.isCountedFromCountries()) return CountCheck.Place.region("" + g.getId(), g.getName() + " (" + g.getGeorank() + ")");
        else if (!Georank.COUNTRY.equals(g.getGeorank())) return null;
        return new CountCheck.Place(CountCheck.GEOLOCALE, "" + g.getId(), g.getName(), column, g.getName());
    }

    static List<CountCheck.Place> places(HttpServletRequest request, Connection connection, List<String> notes) throws SQLException {
        List<CountCheck.Place> places = new ArrayList<>();
        String[] geolocaleIds = request.getParameterValues("geolocaleId");
        String project = request.getParameter("project");
        String museum = request.getParameter("museum");
        String bioregion = request.getParameter("bioregion");
        boolean custom = isCustom(request);

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
            // A subregion (counted from its countries since 2026-10-20): the one of Madagascar.
            Geolocale madagascar = GeolocaleMgr.getValidCountry("Madagascar");
            Geolocale subregion = madagascar == null || madagascar.getParent() == null ? null
                : GeolocaleMgr.getGeolocale(madagascar.getParent(), Georank.SUBREGION);
            if (subregion != null) geolocales.add(subregion); else notes.add("The subregion of Madagascar was not found");
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
            if (p != null) places.add(p); else notes.add(Html.esc(g.getName()) + ": only countries, adm1s, islands, subregions and regions can be checked");
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
            + "morphospecies, the subregion of Madagascar, the largest live curated project, the Malagasy bioregion and the CASC museum), and compares. "
            + "Checked: the specimens of each species, the species of each genus and the genera of each subfamily in the "
            + "place's taxon list, the \"Global\" specimens of each species, and the place's image numbers (Images, "
            + "images of each view, Imaged specimens) and, for countries, islands, states and subregions, the place's "
            + "Specimens number (a subregion's specimens are those collected in its countries). Each day it also recounts a different "
            + "slice of all places, so every country, region, project and museum is recounted within a week and every state within a month. "
            + "And for every place it checks the numbers that must agree: Subfamilies, Genera, Species and Valid species = the rows of "
            + "its list, Specimens = the sum of its species, the pie slices add up, Endemic = the species listed as endemic, image "
            + "numbers in order, every row of every list against the rows under it, the Global specimens, the home page numbers and the "
            + "image counts of taxa and specimens (the list of all numbers and their checks: docs/numbers.md). Every number that "
            + "differs is listed with the number the site shows (\"Stored\") and the number it must be (\"Recount\").",
        "A difference means the site shows a wrong number on that place's page. A \"pending\" difference is expected: "
            + "something changed after the last nightly run (an upload, a name update, a list edit) and the next nightly "
            + "run will count it. A \"real\" difference has no such reason: the nightly count missed something.",
        "Usually nothing: pending differences go away after the next nightly run. If a real difference is still listed "
            + "the next day, after the nightly run has finished, email the list (place, number, taxon, both values) "
            + "to antweb@antweb.org. Between 05:00 and 08:15 Pacific the nightly run is rewriting the numbers, so "
            + "differences are expected then; look again later.");

    /** Checks whose "Taxon" cell is a taxon name (linked to its page). */
    static final java.util.Set<String> LINKED_CHECKS = java.util.Set.of("specimens", "species", "genera", "global", "images");

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
        if (!result.rulesCounted) {
            b.append("<p><b>No nightly run has counted by the rules of 2026-10-21 yet</b> (place totals counted from their lists, pies,"
                + " endemic numbers, taxon image counts, home page numbers): the differences of those numbers are pending until it has.</p>");
        }
        for (String note : result.notes) b.append("<p>Note: ").append(note).append("</p>");
        b.append("<p id=\"count-check-time\" data-seconds=\"").append(result.millis / 1000).append("\">This check took ")
         .append(String.format("%.1f", result.millis / 1000.0)).append(" seconds (every place: ")
         .append(result.invariants == null ? "not run" : String.format("%.1f s", result.invariants.millis / 1000.0))
         .append("; recounts from the specimen records: ").append(String.format("%.1f s", result.recountMillis / 1000.0)).append(").</p>");
        if (result.invariants != null) {
            b.append("<h3>Every place: numbers that must agree</h3>");
            b.append("<table border=1 cellpadding=4 style=\"border-collapse:collapse\" id=\"count-invariants\"><tr><th>Check</th><th>What</th>"
                + "<th>Rows of docs/numbers.md</th><th>Numbers compared</th><th>Real</th><th>Pending</th><th>ms</th></tr>");
            for (CountInvariants.Check c : result.invariants.checks) {
                b.append("<tr class=\"count-invariant\" data-id=\"").append(Html.escAttr(c.id)).append("\"><td>").append(Html.esc(c.id))
                 .append("</td><td>").append(Html.esc(c.title)).append(c.error == null ? "" : " <b>(failed: " + Html.esc(c.error) + ")</b>")
                 .append("</td><td>").append(Html.esc(c.rows)).append("</td><td>").append(c.compared).append("</td><td>").append(c.real)
                 .append("</td><td>").append(c.pending).append("</td><td>").append(c.millis).append("</td></tr>");
            }
            b.append("</table>");
        }
        if (result.rotatingPlanned > 0) {
            b.append("<p id=\"count-check-rotation\" data-planned=\"").append(result.rotatingPlanned).append("\" data-checked=\"")
             .append(result.rotatingChecked).append("\">Today's rotating sample (every place is recounted within a week, adm1s within a month): ")
             .append(result.rotatingChecked).append(" of ").append(result.rotatingPlanned).append(" places recounted");
            if (!result.rotatingSkipped.isEmpty()) {
                b.append("; not this time (time limit): ");
                int n = 0;
                for (CountCheck.Place p : result.rotatingSkipped) {
                    if (n++ > 0) b.append(", ");
                    if (n > 30) { b.append("..."); break; }
                    b.append(Html.esc(p.label));
                }
            }
            b.append(".</p>");
        }
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
                 .append("</td><td>").append(LINKED_CHECKS.contains(m.check) ? Taxon.makeDescriptionLink(m.taxonName) : Html.esc(m.taxonName)).append("</td><td>").append(m.stored)
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

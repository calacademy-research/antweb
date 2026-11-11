package org.calacademy.antweb.util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import com.google.gson.JsonObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;

/**
 * The "Daily update" page (dailyUpdate.do, 2026-11-07): where AntWeb's daily update (the Scheduler's run at 05:00
 * Pacific, DailyUpdate) is: each step in order with its status (done, running, waiting, skipped, failed), start, end,
 * duration and usual time; a running step shows its elapsed time and estimated end, a step over 1.5 times its usual time
 * is yellow, a failed one red. Curators and admins (util/dailyUpdate.jsp checks too).
 *
 * dailyUpdate.do?format=json needs no login (no personal data): {"runs":[the last JSON_RUNS runs, newest first]} with
 * each run's started, ended (null while it runs), state (running, ok, failed) and steps (name, label, state, started,
 * ended, usual_seconds, message); times ISO 8601 without zone, Pacific. Read by the production morning check.
 * Tests: DailyUpdateTest (JSON shape), tests/e2e/test_daily_update.py.
 */
public final class DailyUpdateAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DailyUpdateAction.class);

    static final int JSON_RUNS = 2;
    static final int PAGE_RUNS = 7;

    /** One run as shown: its start, its steps, and whether it is going on in this server now. */
    static final class RunView {
        Date start;
        List<DailyUpdate.StepView> steps;
        boolean active;
    }

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        boolean json = "json".equals(request.getParameter("format"));
        if (!json) {
            ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;
            ActionForward c = Check.curator(request, mapping); if (c != null) return c;
        }
        List<RunView> runs = new ArrayList<>();
        String problem = null;
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("DailyUpdateAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            runs = load(connection, json ? JSON_RUNS : PAGE_RUNS);
        } catch (SQLException e) {
            // No table yet (before the first start-up with this code) or no database: an empty list, said on the page.
            s_log.warn("execute() e:" + e);
            problem = "The daily update steps could not be read (" + e.getMessage() + ").";
            runs = activeOnly();
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }

        if (json) {
            try {
                List<JsonObject> out = new ArrayList<>();
                for (RunView r : runs) out.add(DailyUpdate.runJson(r.start, r.steps, r.active));
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write(DailyUpdate.json(out));
            } catch (IOException e) {
                s_log.warn("execute() json e:" + e);
            }
            return null;
        }
        request.setAttribute("html", html(runs, problem, System.currentTimeMillis()));
        return mapping.findForward("success");
    }

    /** The last n runs, newest first; the run going on in this server from memory (its rows are written a moment later). */
    static List<RunView> load(Connection connection, int n) throws SQLException {
        List<RunView> out = new ArrayList<>();
        Date activeStart = DailyUpdate.activeRunStart();
        List<DailyUpdate.StepView> activeSteps = DailyUpdate.activeView();
        long now = System.currentTimeMillis();
        if (activeStart != null && activeSteps != null) {
            RunView r = new RunView();
            r.start = activeStart;
            r.steps = activeSteps;
            r.active = true;
            out.add(r);
        }
        for (Date start : DailyUpdateDb.lastRuns(connection, n)) {
            if (out.size() >= n) break;
            if (start.equals(activeStart)) continue;
            Map<String, Long> usual = DailyUpdateDb.usual(connection, start, DailyUpdate.USUAL_RUNS);
            RunView r = new RunView();
            r.start = start;
            r.steps = DailyUpdate.view(DailyUpdateDb.rows(connection, start), usual, false, now);
            r.active = false;
            out.add(r);
        }
        return out;
    }

    static List<RunView> activeOnly() {
        List<RunView> out = new ArrayList<>();
        Date start = DailyUpdate.activeRunStart();
        List<DailyUpdate.StepView> steps = DailyUpdate.activeView();
        if (start != null && steps != null) {
            RunView r = new RunView();
            r.start = start;
            r.steps = steps;
            r.active = true;
            out.add(r);
        }
        return out;
    }

    static final String INTRO = "<div class=\"aw-intro\">"
        + "<p>Every day at 05:00 Pacific time AntWeb runs its daily update: it loads AntCat's names and the CAS specimens from "
        + "TaxonWorks, then recounts every list, place, image and statistic the site shows. It usually takes two to three hours; "
        + "while it runs some pages may be slow or show yesterday's numbers.</p>"
        + "<p>This page shows each step in the order it runs, which one is running, and how long each usually takes "
        + "(the median of the last " + DailyUpdate.USUAL_RUNS + " daily updates). A step taking more than 1.5 times its usual time "
        + "is yellow, a failed step red; the administrators get an email when a step fails or runs over twice its usual time. "
        + "The Count check step is also yellow when it found real differences (numbers on the site that do not equal their "
        + "recount): it says how many, the administrators get an email, and Check the counts lists them. "
        + "Nothing to do here unless a step is red (then see compute.log in the Log Manager) or the Count check is yellow. "
        + "All times are Pacific time.</p>"
        + "</div>";

    static String statusClass(String state) {
        switch (state) {
            case DailyUpdate.DONE: return "aw-status aw-status-finished";
            case DailyUpdate.RUNNING: return "aw-status aw-status-running";
            case DailyUpdate.FAILED: return "aw-status aw-status-failed";
            default: return "aw-status aw-status-queued";
        }
    }

    static String html(List<RunView> runs, String problem, long now) {
        StringBuilder b = new StringBuilder(INTRO);
        if (problem != null) b.append("<div class=\"aw-warn\">").append(Html.esc(problem)).append("</div>");
        if (runs.isEmpty()) {
            b.append("<div class=\"aw-card\" id=\"daily-update-none\"><p>No daily update has been recorded yet. "
                + "The first one is recorded at the next 05:00 Pacific.</p></div>");
            return b.toString();
        }
        for (int i = 0; i < runs.size(); i++) {
            RunView r = runs.get(i);
            if (i == 0) b.append(runCard(r));
            else {
                if (i == 1) b.append("<div class=\"aw-card\" id=\"daily-update-earlier\"><h2>Earlier daily updates</h2>");
                b.append("<details><summary>").append(Html.esc(summary(r))).append("</summary>").append(table(r)).append("</details>");
                if (i == runs.size() - 1) b.append("</div>");
            }
        }
        return b.toString();
    }

    static String summary(RunView r) {
        String state = DailyUpdate.overallState(r.steps, r.active);
        Date end = null;
        for (DailyUpdate.StepView v : r.steps) if (DailyUpdate.END.equals(v.step.key)) end = v.ended;
        String day = DailyUpdate.iso(r.start);
        day = day == null ? "" : day.substring(0, 10);
        return day + ", started " + DailyUpdate.hhmm(r.start) + (end != null ? ", ended " + DailyUpdate.hhmm(end) : "") + ": "
            + (DailyUpdate.RUN_OK.equals(state) ? "finished" : DailyUpdate.RUN_RUNNING.equals(state) ? "running" : "failed");
    }

    static String runCard(RunView r) {
        StringBuilder b = new StringBuilder("<div class=\"aw-card\" id=\"daily-update-latest\">");
        String state = DailyUpdate.overallState(r.steps, r.active);
        b.append("<h2>").append(r.active ? "Today's daily update (running)" : "Last daily update").append("</h2>");
        b.append("<p class=\"aw-lead\" id=\"daily-update-state\" data-state=\"").append(state).append("\">").append(Html.esc(summary(r)));
        if (r.active) {
            String hover = DailyUpdate.hover(r.steps);
            if (hover != null) b.append(". ").append(Html.esc(hover));
            Long left = DailyUpdate.minutesLeft(r.steps);
            if (left != null) b.append(". About ").append(left).append(" minutes left");
        }
        b.append(".</p>");
        b.append(table(r));
        b.append("</div>");
        return b.toString();
    }

    static String table(RunView r) {
        StringBuilder b = new StringBuilder("<table class=\"daily-update-steps\"><tr><th>#</th><th>Step</th><th>Status</th>"
            + "<th>Started (Pacific)</th><th>Ended (Pacific)</th><th>Duration</th><th>Usual</th></tr>");
        for (DailyUpdate.StepView v : r.steps) {
            String style = DailyUpdate.FAILED.equals(v.state) ? " style=\"background:#fdecea\""
                : v.slow || v.warning ? " style=\"background:#fff3cd\"" : "";
            b.append("<tr data-step=\"").append(v.step.key).append("\" data-state=\"").append(v.state).append("\"")
             .append(v.slow ? " data-slow=\"1\"" : "").append(v.warning ? " data-warning=\"1\"" : "").append(style).append(">");
            b.append("<td>").append(v.number).append("</td>");
            b.append("<td><b>").append(Html.esc(v.step.label)).append("</b><br><span class=\"aw-note\">").append(Html.esc(v.step.what)).append("</span>");
            if (v.message != null && !v.message.isEmpty()) b.append("<br>").append(Html.esc(v.message));
            b.append("</td>");
            b.append("<td><span class=\"").append(statusClass(v.state)).append("\">").append(v.state).append("</span></td>");
            b.append("<td>").append(DailyUpdate.hhmm(v.started)).append("</td>");
            b.append("<td>").append(DailyUpdate.hhmm(v.ended)).append("</td>");
            b.append("<td>");
            if (DailyUpdate.RUNNING.equals(v.state)) {
                b.append(DailyUpdate.duration(v.seconds)).append(" so far");
                if (v.estimatedEnd != null) b.append(", estimated end ").append(DailyUpdate.hhmm(v.estimatedEnd));
            } else {
                b.append(DailyUpdate.duration(v.seconds));
            }
            b.append("</td>");
            b.append("<td>").append(DailyUpdate.duration(v.usual)).append("</td>");
            b.append("</tr>");
        }
        b.append("</table>");
        return b.toString();
    }
}

package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.util.BackgroundJobs;
import org.calacademy.antweb.util.JobStatusAction;
import org.calacademy.antweb.util.JobSummary;

/**
 * A nightly set started by hand keeps one plain sentence as its job result, and the job page never shows a
 * whole AntWeb page inside itself (Brian Fisher, 2026-10-22: data job 186 showed the AntWeb header, the search
 * boxes, "set2 - {operation:dataCleanup} ... in 9.73 mins." and the site footer above its details).
 */
public class JobSummaryTest extends TestCase {

    /** What utilData.do?action=set2 answered with: a whole page in the public layout (shortened). */
    static final String SET2_PAGE = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\"><html><head><title>message...</title>"
        + "<script>var x = 1;</script></head><body><div id=\"site_header\"><a href=\"/\">AntWeb</a> v9.2.0 &middot; cbf9123"
        + " <a href=\"/login.do\">Login</a><form><input type=\"submit\" value=\"Go\"></form></div>"
        + "<div class=\"wide_left\"><br><br><b>set2 - {operation:dataCleanup} {operation:generateGeolocaleTaxaFromSpecimens}"
        + " {operation:updateTaxonSets} {operation:geolocaleCountCrawl} {operation:geolocaleTaxonFix} in 9.73 mins.</b></div>"
        + "<div id=\"footer\">Copyright California Academy of Sciences. Cite AntWeb. Licence. Funding.</div></body></html>";

    static final String SCHEDULER_RESULT = "Scheduler run:2 completed in 9.80 mins.  output:" + SET2_PAGE;

    static final String SENTENCE = "Nightly set 2 finished in 9.73 minutes: data clean-up, place species lists from specimens,"
        + " list names brought up to date, place counts, place list fixes.";

    public void testSchedulerResultIsOneSentence() {
        assertEquals(SENTENCE, JobSummary.scheduler(SCHEDULER_RESULT));
    }

    public void testEverySetOfAFullRun() {
        String two = SET2_PAGE.replace("set2 - {operation:dataCleanup} {operation:generateGeolocaleTaxaFromSpecimens}"
            + " {operation:updateTaxonSets} {operation:geolocaleCountCrawl} {operation:geolocaleTaxonFix} in 9.73 mins.",
            "set4 - {operation:imageCountCrawl} {operation:placeImageCounts} statistics in 3.10 mins.");
        String s = JobSummary.scheduler("Scheduler run:0 completed in 13.00 mins. output:" + SET2_PAGE + two);
        assertEquals(SENTENCE + " Nightly set 4 finished in 3.10 minutes: image counts, place image counts, statistics.", s);
    }

    public void testUnknownOperationInWordsAndNoOutput() {
        assertTrue(JobSummary.scheduler("Scheduler run:3 completed in 1.5 mins. output:set3 - {operation:fooBarCrawl} in 1.20 mins.")
            .contains("foo bar crawl"));
        assertEquals("Nightly set 5 finished in 2.00 minutes. Details in compute.log (Log Manager).",
            JobSummary.scheduler("Scheduler run:5 completed in 2.00 mins. output:"));
    }

    public void testOldStoredResultShownWithoutThePage() {
        String shown = JobSummary.display(SCHEDULER_RESULT);
        assertEquals(SENTENCE, shown);
        assertFalse(shown.contains("Copyright"));
        assertFalse(shown.contains("Login"));
        assertFalse(shown.contains("<"));
        // A message page that is not a set: its message only.
        assertEquals("Done &lt;now&gt;", JobSummary.display("<html><body><div id=\"site_header\">AntWeb Login</div>"
            + "<div class=\"wide_left\"><b>Done &lt;now&gt;</b></div><div>Copyright</div></body></html>"));
        // AntWeb's own short result HTML (the count check summary) stays as it is.
        assertEquals("<p>3 real differences</p>", JobSummary.display("<p>3 real differences</p>"));
    }

    public void testDataJobLine() {
        assertEquals("Nightly set 3 finished in 0.50 minutes: project counts, museum lists and counts.",
            JobSummary.dataJob("set3 -  {operation:projectCountCrawl} {operation:populateMuseum} in 0.50 mins."));
        assertEquals("Recent content generated.", JobSummary.dataJob("Recent content generated."));
    }

    public void testJobPageHasNoSecondSiteHeader() {
        BackgroundJobs.Job job = new BackgroundJobs.Job(BackgroundJobs.DATA, "schedule:run:2", "Nightly set 2 (started by hand)", 1, "admin", 0);
        // As stored before the fix (BackgroundJobDb reads rows back with restore()).
        job.restore(186, BackgroundJobs.Status.finished, "", SCHEDULER_RESULT, null, null, new java.util.Date(), new java.util.Date(),
            new java.util.Date());
        String page = JobStatusAction.jobHtml(job, false, true, "https://example.org");
        assertTrue(page, page.contains(SENTENCE));
        assertFalse(page, page.contains("Copyright"));
        assertFalse(page, page.contains("site_header"));
        assertFalse(page, page.contains("<html"));
        assertFalse(page, page.contains("<script"));
        assertEquals("one title", 1, page.split("<h1>", -1).length - 1);
    }
}

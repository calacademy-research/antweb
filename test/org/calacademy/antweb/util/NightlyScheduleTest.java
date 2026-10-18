package test.org.calacademy.antweb.util;

import java.io.File;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import junit.framework.TestCase;
import org.calacademy.antweb.util.Scheduler;
import org.calacademy.antweb.util.SessionRequestFilter;

/** Nightly run: the restart setting (etc/scheduler.properties) and the 5 AM re-anchoring. */
public class NightlyScheduleTest extends TestCase {

    public void testRestartSettingDefaultsOff() throws Exception {
        assertFalse(Scheduler.isNightlyRestartOn(null));
        assertFalse(Scheduler.isNightlyRestartOn(new File("/nonexistent/scheduler.properties")));

        File f = File.createTempFile("scheduler", ".properties");
        f.deleteOnExit();
        write(f, "# nothing set\n");
        assertFalse(Scheduler.isNightlyRestartOn(f));
        write(f, "nightlyRestart=false\n");
        assertFalse(Scheduler.isNightlyRestartOn(f));
        write(f, "nightlyRestart = true \n");
        assertTrue(Scheduler.isNightlyRestartOn(f));

        assertTrue(Scheduler.isOn("on"));
        assertTrue(Scheduler.isOn("YES"));
        assertTrue(Scheduler.isOn("1"));
        assertFalse(Scheduler.isOn(""));
        assertFalse(Scheduler.isOn("0"));
        assertFalse(Scheduler.isOn(null));
    }

    public void testNextLaunchIsAlwaysFiveAmLocal() {
        TimeZone la = TimeZone.getTimeZone("America/Los_Angeles");

        // Before 5 AM: today. At or after 5 AM: tomorrow.
        assertLaunch(la, at(la, 2026, Calendar.OCTOBER, 20, 4, 59), 2026, Calendar.OCTOBER, 20);
        assertLaunch(la, at(la, 2026, Calendar.OCTOBER, 20, 5, 0), 2026, Calendar.OCTOBER, 21);
        assertLaunch(la, at(la, 2026, Calendar.OCTOBER, 20, 8, 10), 2026, Calendar.OCTOBER, 21);

        // Across the end of daylight saving time (1 Nov 2026) the run stays at 5:00, not 4:00.
        assertLaunch(la, at(la, 2026, Calendar.OCTOBER, 31, 8, 0), 2026, Calendar.NOVEMBER, 1);
        // And across its start (14 Mar 2027), at 5:00, not 6:00.
        assertLaunch(la, at(la, 2027, Calendar.MARCH, 13, 8, 0), 2027, Calendar.MARCH, 14);
    }

    private static Calendar at(TimeZone tz, int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, m, d, h, min, 0);
        return c;
    }

    private static void assertLaunch(TimeZone tz, Calendar now, int y, int m, int d) {
        Date next = SessionRequestFilter.nextLaunchTime(now);
        Calendar c = Calendar.getInstance(tz);
        c.setTime(next);
        assertEquals(y, c.get(Calendar.YEAR));
        assertEquals(m, c.get(Calendar.MONTH));
        assertEquals(d, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(Scheduler.LAUNCHTIME, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, c.get(Calendar.MINUTE));
        assertTrue(next.after(now.getTime()));
    }

    private static void write(File f, String text) throws Exception {
        try (FileWriter w = new FileWriter(f)) {
            w.write(text);
        }
    }
}

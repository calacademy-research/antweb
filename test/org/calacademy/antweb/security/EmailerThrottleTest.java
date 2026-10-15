package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.util.Emailer;

/** Mail limits: same subject at most hourly, at most 100 a day. */
public class EmailerThrottleTest extends TestCase {

    public void testSameSubjectOncePerHour() {
        Emailer.resetThrottle();
        long t = 1_000_000_000L;
        assertNull(Emailer.throttle("Nightly failed", t));
        assertNotNull(Emailer.throttle("Nightly failed", t + 60_000));
        assertNull(Emailer.throttle("Other", t + 60_000));
        assertNull(Emailer.throttle("Nightly failed", t + 61 * 60_000));
    }

    public void testDailyLimit() {
        Emailer.resetThrottle();
        long t = 2_000_000_000L;
        for (int i = 0; i < 100; i++) assertNull(Emailer.throttle("s" + i, t));
        assertNotNull(Emailer.throttle("one more", t));
        assertNull(Emailer.throttle("next day", t + 25 * 3600_000L));
    }
}

package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.util.PasswordResetMgr;
import org.calacademy.antweb.util.RememberMeMgr;
import org.calacademy.antweb.util.RememberMeMgr.Verdict;

/** "Keep me logged in": cookie format, hashing, constant-time compare, expiry and the login decision. */
public class RememberMeTest extends TestCase {

    public void testTokensAndParse() {
        String sel = RememberMeMgr.newSelector();
        String val = RememberMeMgr.newValidator();
        assertEquals(16, sel.length());
        assertEquals(43, val.length());
        assertFalse(val.equals(RememberMeMgr.newValidator()));
        String[] p = RememberMeMgr.parse(RememberMeMgr.format(sel, val));
        assertNotNull(p);
        assertEquals(sel, p[0]);
        assertEquals(val, p[1]);

        assertNull(RememberMeMgr.parse(null));
        assertNull(RememberMeMgr.parse(""));
        assertNull(RememberMeMgr.parse(sel + val));                       // no separator
        assertNull(RememberMeMgr.parse(sel + ":" + val + "x"));           // too long
        assertNull(RememberMeMgr.parse(sel.substring(1) + "+:" + val));   // bad character
        assertNull(RememberMeMgr.parse(sel + ":" + val.substring(1) + "=")); // padding
        assertNull(RememberMeMgr.parse(sel + ";" + val));
        assertNull(RememberMeMgr.parse("'or 1=1 -- :" + val));
    }

    public void testHash() {
        String v = RememberMeMgr.newValidator();
        String h = RememberMeMgr.hash(v);
        assertEquals(PasswordResetMgr.hashToken(v), h);
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertTrue(RememberMeMgr.hashEquals(h, RememberMeMgr.hash(v)));
        assertFalse(RememberMeMgr.hashEquals(h, RememberMeMgr.hash(v + "x")));
        assertFalse(RememberMeMgr.hashEquals(h, null));
        assertFalse(RememberMeMgr.hashEquals(null, null));
        assertFalse(RememberMeMgr.hashEquals(h, h.substring(1)));
    }

    private static final long DAY = 24 * 3600;

    public void testCheck() {
        String v = RememberMeMgr.newValidator();
        String h = RememberMeMgr.hash(v);
        String other = RememberMeMgr.hash(RememberMeMgr.newValidator());

        assertEquals(Verdict.OK, RememberMeMgr.check(v, h, null, 30 * DAY, -1, true, false, 3, 3));
        // Wrong validator.
        assertEquals(Verdict.MISMATCH, RememberMeMgr.check(v, other, null, 30 * DAY, -1, true, false, 3, 3));
        // Expired (database says no time left).
        assertEquals(Verdict.EXPIRED, RememberMeMgr.check(v, h, null, 0, -1, true, false, 3, 3));
        assertEquals(Verdict.EXPIRED, RememberMeMgr.check(v, h, null, -5, -1, true, false, 3, 3));
        assertEquals(Verdict.OK, RememberMeMgr.check(v, h, null, 1, -1, true, false, 3, 3));
        // Login deleted, must reset, or sessions ended (password change, reset, campaign).
        assertEquals(Verdict.NO_LOGIN, RememberMeMgr.check(v, h, null, DAY, -1, false, false, 3, 0));
        assertEquals(Verdict.MUST_RESET, RememberMeMgr.check(v, h, null, DAY, -1, true, true, 3, 3));
        assertEquals(Verdict.STALE, RememberMeMgr.check(v, h, null, DAY, -1, true, false, 3, 4));
        // Previous validator, just rotated by a parallel request: accepted without a new rotation.
        assertEquals(Verdict.GRACE, RememberMeMgr.check(v, other, h, DAY, 5, true, false, 3, 3));
        assertEquals(Verdict.GRACE, RememberMeMgr.check(v, other, h, DAY, RememberMeMgr.GRACE_SECONDS, true, false, 3, 3));
        // ... but not after the grace time, nor if never rotated.
        assertEquals(Verdict.MISMATCH, RememberMeMgr.check(v, other, h, DAY, RememberMeMgr.GRACE_SECONDS + 1, true, false, 3, 3));
        assertEquals(Verdict.MISMATCH, RememberMeMgr.check(v, other, h, DAY, -1, true, false, 3, 3));
        // Grace does not bypass the other checks.
        assertEquals(Verdict.STALE, RememberMeMgr.check(v, other, h, DAY, 5, true, false, 3, 4));
    }
}

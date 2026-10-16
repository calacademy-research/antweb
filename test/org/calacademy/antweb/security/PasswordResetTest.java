package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.util.Emailer;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.PasswordPolicy;
import org.calacademy.antweb.util.PasswordResetMgr;

/** Password rules, reset tokens, masking, transactional mail limit, log redaction. */
public class PasswordResetTest extends TestCase {

    public void testPolicy() {
        assertNull(PasswordPolicy.check("correct horse battery", "correct horse battery", "jsmith", "j@x.org"));
        assertNotNull(PasswordPolicy.check("", "", "jsmith", "j@x.org"));
        assertNotNull(PasswordPolicy.check("correct horse battery", "correct horse batterY", "jsmith", "j@x.org"));
        assertNotNull(PasswordPolicy.check("short1", "short1", "jsmith", "j@x.org"));
        assertNotNull(PasswordPolicy.check("JohnSmith1234", "JohnSmith1234", "johnsmith1234", "j@x.org"));
        assertNotNull(PasswordPolicy.check("john@example.org", "john@example.org", "jsmith", "John@Example.org"));
        assertNotNull(PasswordPolicy.check("Password1234", "Password1234", "jsmith", "j@x.org"));
        assertNotNull(PasswordPolicy.check("123456789012", "123456789012", "jsmith", "j@x.org"));
    }

    public void testTokens() {
        String t = PasswordResetMgr.newToken();
        assertEquals(43, t.length());
        assertTrue(PasswordResetMgr.isWellFormed(t));
        assertFalse(t.equals(PasswordResetMgr.newToken()));
        assertFalse(PasswordResetMgr.isWellFormed(null));
        assertFalse(PasswordResetMgr.isWellFormed("abc"));
        assertFalse(PasswordResetMgr.isWellFormed(t.substring(1) + "+"));
        String h = PasswordResetMgr.hashToken(t);
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PasswordResetMgr.hashToken("abc"));
    }

    public void testMaskEmail() {
        assertEquals("j***@d***.org", PasswordResetMgr.maskEmail("john@domain.org"));
        assertEquals("b***@c***.org", PasswordResetMgr.maskEmail("bfisher@calacademy.org"));
        assertEquals("", PasswordResetMgr.maskEmail(null));
    }

    public void testIpLimit() {
        String ip = "203.0.113." + System.nanoTime() % 200;
        for (int i = 0; i < PasswordResetMgr.MAX_PER_IP_PER_HOUR; i++) assertTrue(PasswordResetMgr.allowIp(ip));
        assertFalse(PasswordResetMgr.allowIp(ip));
    }

    public void testTransactionalLimitIgnoresSubject() {
        Emailer.resetThrottle();
        long t = 3_000_000_000L;
        for (int i = 0; i < 2000; i++) assertNull(Emailer.throttleTransactional(t));
        assertNotNull(Emailer.throttleTransactional(t));
        assertNull(Emailer.throttleTransactional(t + 25 * 3600_000L));
        Emailer.resetThrottle();
    }

    public void testLogRedaction() {
        assertEquals("https://x/resetPassword.do?token=*** referer:-",
            HttpUtil.forLog("https://x/resetPassword.do?token=AbC_d-1 referer:-"));
        assertEquals("a.do?retypePassword=***&x=1", HttpUtil.forLog("a.do?retypePassword=s3cret&x=1"));
    }
}

package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.util.PasswordHash;

/**
 * tools/staging/create-test-accounts.py writes login.password hashes made in Python
 * (hashlib.pbkdf2_hmac). AntWeb must accept them: the same format and parameters as
 * PasswordHash. The Python side checks a hash made here (tests/unit/test_staging_accounts.py).
 */
public class PasswordHashCompatTest extends TestCase {

    // "zz-Test pässword 1"
    private static final String PLAIN = "zz-Test pässword 1";
    // Made by create-test-accounts.py hash_password(PLAIN).
    private static final String FROM_PYTHON =
        "pbkdf2_sha256$600000$EFLAnAOcwM/wlxNQAfym7Q==$avqXmaL6ssODHyTONRhEmnEgtCoAyMZ1gZohd5KKDVU=";

    public void testPythonHashIsAccepted() {
        assertTrue(PasswordHash.isHashed(FROM_PYTHON));
        assertFalse("600,000 iterations: no rehash at login", PasswordHash.needsRehash(FROM_PYTHON));
        assertTrue(PasswordHash.matches(FROM_PYTHON, PLAIN));
        assertFalse(PasswordHash.matches(FROM_PYTHON, "zz-Test password 1"));
        assertFalse(PasswordHash.matches(FROM_PYTHON, ""));
    }

    public void testFormatOfANewHash() {
        String h = PasswordHash.hash(PLAIN);
        String[] parts = h.split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2_sha256", parts[0]);
        assertEquals("600000", parts[1]);
        assertEquals("16-byte salt, base64 with padding", 24, parts[2].length());
        assertEquals("32-byte hash, base64 with padding", 44, parts[3].length());
    }
}

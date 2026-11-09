package test.org.calacademy.antweb.security;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.ApiKeysAction;
import org.calacademy.antweb.home.ApiKeyDb;
import org.calacademy.antweb.util.ApiKeyMgr;
import org.calacademy.antweb.util.ApiKeys;

/**
 * Free API keys, step 6 PRs 4 and 5 (Brian Fisher, 2026-11-08), the rules without a database: key format and
 * hashing, confirmation tokens and their expiry, the registration throttles, the form checks, admin limits, the
 * CSV download and the email texts (no key, no token in the copy, no em-dash). Database side: home/ApiKeyDbTest.
 */
public class ApiKeysTest extends TestCase {

    public void testKeyFormat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String k = ApiKeys.newKey();
            assertEquals(35, k.length());
            assertTrue(k, k.startsWith("aw_"));
            assertTrue(k, k.substring(3).matches("[A-Za-z0-9_-]{32}"));
            assertTrue(ApiKeys.isWellFormedKey(k));
            assertEquals(7, ApiKeys.prefixOf(k).length());
            assertEquals(k.substring(0, 7), ApiKeys.prefixOf(k));
            assertTrue("keys repeat", seen.add(k));
        }
        assertFalse(ApiKeys.isWellFormedKey(null));
        assertFalse(ApiKeys.isWellFormedKey("aw_short"));
        assertFalse(ApiKeys.isWellFormedKey("ac_" + "a".repeat(32)));
        assertFalse(ApiKeys.isWellFormedKey("aw_" + "a".repeat(31) + "+"));
    }

    public void testHashing() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApiKeys.sha256("abc"));
        String k = ApiKeys.newKey();
        String h = ApiKeys.sha256(k);
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertFalse(h.contains(k.substring(3, 10)));
        assertEquals(h, ApiKeys.sha256(k));
    }

    public void testTokensAndPlaceholders() {
        String t = ApiKeys.newConfirmToken();
        assertEquals(43, t.length());
        assertTrue(ApiKeys.isWellFormedToken(t));
        assertFalse(t.equals(ApiKeys.newConfirmToken()));
        assertFalse(ApiKeys.isWellFormedToken(null));
        assertFalse(ApiKeys.isWellFormedToken(t.substring(1)));
        assertFalse(ApiKeys.isWellFormedToken(t.substring(1) + "'"));
        String p = ApiKeys.placeholderPrefix();
        assertTrue(p, p.startsWith("p_") && p.length() <= 16);
        assertFalse(p.startsWith(ApiKeys.KEY_START));
        assertTrue(ApiKeys.placeholderHash().matches("[0-9a-f]{64}"));
    }

    public void testConfirmLinkExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 8, 12, 0);
        assertEquals(ApiKeys.ConfirmState.VALID, ApiKeys.confirmState("pending", now.plusHours(24), now));
        assertEquals(ApiKeys.ConfirmState.VALID, ApiKeys.confirmState("pending", now.plusSeconds(1), now));
        assertEquals(ApiKeys.ConfirmState.EXPIRED, ApiKeys.confirmState("pending", now, now));
        assertEquals(ApiKeys.ConfirmState.EXPIRED, ApiKeys.confirmState("pending", now.minusMinutes(1), now));
        assertEquals(ApiKeys.ConfirmState.EXPIRED, ApiKeys.confirmState("pending", null, now));
        assertEquals(ApiKeys.ConfirmState.USED, ApiKeys.confirmState("active", now.plusHours(1), now));
        assertEquals(ApiKeys.ConfirmState.CANCELLED, ApiKeys.confirmState("revoked", now.plusHours(1), now));
        assertEquals(ApiKeys.ConfirmState.UNKNOWN, ApiKeys.confirmState(null, null, now));
        assertEquals(ApiKeys.ConfirmState.UNKNOWN, ApiKeys.confirmState("other", now.plusHours(1), now));
        assertEquals(24, ApiKeys.CONFIRM_HOURS);
    }

    public void testThrottles() {
        ApiKeys.SlidingWindow ip = new ApiKeys.SlidingWindow(ApiKeys.MAX_PER_IP_PER_HOUR, 3_600_000L);
        long t = 1_000_000L;
        for (int i = 0; i < 5; i++) assertTrue("registration " + (i + 1), ip.allow("1.2.3.4", t + i));
        assertFalse("the sixth in an hour", ip.allow("1.2.3.4", t + 10));
        assertTrue("another address", ip.allow("5.6.7.8", t + 10));
        assertFalse("still within the hour", ip.allow("1.2.3.4", t + 3_599_999L));
        assertTrue("an hour after the first", ip.allow("1.2.3.4", t + 3_600_000L));
        assertTrue(ip.allow(null, t));
        assertEquals(5, ApiKeys.MAX_PER_IP_PER_HOUR);
        assertTrue(ApiKeys.allowEmail(0));
        assertTrue(ApiKeys.allowEmail(2));
        assertFalse(ApiKeys.allowEmail(3));
        assertTrue(ApiKeys.allowAll(99));
        assertFalse(ApiKeys.allowAll(100));
    }

    public void testRegistrationForm() {
        ApiKeys.Registration ok = new ApiKeys.Registration("  Ana Silva ", " Ana@Example.ORG ", "", "Distribution maps of Strumigenys", true);
        assertNull(ok.problem());
        assertEquals("Ana Silva", ok.name);
        assertEquals("ana@example.org", ok.email);
        assertNotNull(new ApiKeys.Registration("", "a@b.org", "", "x", true).problem());
        assertNotNull(new ApiKeys.Registration("A", "", "", "x", true).problem());
        assertNotNull(new ApiKeys.Registration("A", "not an email", "", "x", true).problem());
        assertNotNull(new ApiKeys.Registration("A", "a@b", "", "x", true).problem());
        assertNotNull("project required", new ApiKeys.Registration("A", "a@b.org", "CAS", "  ", true).problem());
        assertNotNull("terms not agreed", new ApiKeys.Registration("A", "a@b.org", "CAS", "x", false).problem());
        assertNotNull(new ApiKeys.Registration("A".repeat(201), "a@b.org", "", "x", true).problem());
        assertNotNull(new ApiKeys.Registration("A", "a@b.org", "", "x".repeat(4001), true).problem());
        // A line break in the address (mail header injection) is never accepted.
        assertNotNull(new ApiKeys.Registration("A", "a@b.org\nBcc: c@d.org", "", "x", true).problem());
        assertNotNull(new ApiKeys.Registration("A", "<a@b.org>", "", "x", true).problem());
        ApiKeys.Registration multi = new ApiKeys.Registration("A\nB", "a@b.org", "", "line 1\r\nline 2\u0007", true);
        assertEquals("A B", multi.name);
        assertEquals("line 1\nline 2", multi.project);
        assertEquals("", ApiKeys.clean(null, true));
    }

    public void testLimitsTypedByAnAdmin() {
        assertNull(ApiKeys.parseLimit("x", null));
        assertNull(ApiKeys.parseLimit("x", "  "));
        assertEquals(Integer.valueOf(5000), ApiKeys.parseLimit("x", "5,000"));
        assertEquals(Integer.valueOf(0), ApiKeys.parseLimit("x", "0"));
        assertEquals(Integer.valueOf(10_000_000), ApiKeys.parseLimit("x", "10000000"));
        for (String bad : new String[] {"-1", "abc", "1.5", "10000001", "9999999999"}) {
            try {
                ApiKeys.parseLimit("Requests a day", bad);
                fail(bad);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().startsWith("Requests a day"));
            }
        }
        assertTrue(ApiKeys.isLevel("collaborator"));
        assertFalse(ApiKeys.isLevel(null));
        assertFalse(ApiKeys.isStatus("Active"));
        assertTrue(ApiKeys.isStatus("revoked"));
    }

    public void testCsvEscaping() {
        assertEquals("", ApiKeys.csvField(null));
        assertEquals("plain", ApiKeys.csvField("plain"));
        assertEquals("12", ApiKeys.csvField(12));
        assertEquals("\"a, b\"", ApiKeys.csvField("a, b"));
        assertEquals("\"say \"\"hi\"\"\"", ApiKeys.csvField("say \"hi\""));
        assertEquals("\"two\nlines\"", ApiKeys.csvField("two\nlines"));
        // A value a spreadsheet would run as a formula is kept as text.
        assertEquals("'=HYPERLINK(1)", ApiKeys.csvField("=HYPERLINK(1)"));
        assertEquals("'+1", ApiKeys.csvField("+1"));
        assertEquals("'-2", ApiKeys.csvField("-2"));
        assertEquals("'@x", ApiKeys.csvField("@x"));
        assertEquals("\"'=a,b\"", ApiKeys.csvField("=a,b"));
        assertEquals("a,\"b,c\",\r\n", ApiKeys.csvLine("a", "b,c", null));
    }

    public void testAdminCsv() {
        ApiKeyDb.Usage noKey = new ApiKeyDb.Usage();
        noKey.today = 7; noKey.last30 = 900; noKey.refused30 = 12;
        List<ApiKeyDb.Row> rows = new ArrayList<>();
        ApiKeyDb.Row active = new ApiKeyDb.Row();
        active.id = 2; active.name = "Ana, Silva"; active.email = "ana@example.org"; active.project = "Maps\nof ants";
        active.level = "registered"; active.status = "active"; active.prefix = "aw_Ab12";
        active.created = LocalDateTime.of(2026, 11, 8, 9, 5); active.perDay = 10000;
        active.usage.today = 3; active.usage.records30 = 1500;
        ApiKeyDb.Row pending = new ApiKeyDb.Row();
        pending.id = 1; pending.name = "Bo"; pending.email = "bo@example.org"; pending.project = "x";
        pending.level = "registered"; pending.status = "pending"; pending.prefix = "p_placeholder12";
        rows.add(active);
        rows.add(pending);
        String csv = ApiKeysAction.csv(rows, noKey);
        String[] lines = csv.split("\r\n", -1);
        assertEquals(5, lines.length);  // header, no key, two keys, and the end of the last line
        assertTrue(lines[0], lines[0].startsWith("number,name,email,institution,project,level,status,"));
        assertEquals(ApiKeysAction.CSV_HEADER.length, lines[0].split(",").length);
        assertTrue(lines[1], lines[1].startsWith("0,All requests without a key,") && lines[1].contains(",7,0,900,0,12,"));
        assertTrue(lines[2], lines[2].startsWith("2,\"Ana, Silva\",ana@example.org,,\"Maps\nof ants\",registered,active,aw_Ab12,2026-11-08 09:05,"));
        assertTrue(lines[2], lines[2].contains(",10000,"));
        assertFalse("a pending row has no key yet", csv.contains("p_placeholder12"));
        assertEquals("", lines[4]);
    }

    public void testEmailTexts() {
        String link = "https://www.antweb.org/apiKeyConfirm.do?token=" + ApiKeys.newConfirmToken();
        String body = ApiKeyMgr.confirmationEmailBody("Ana <b>Silva</b>", link);
        assertTrue(body.contains(link));
        assertTrue(body.contains("Ana &lt;b&gt;Silva&lt;/b&gt;"));
        assertTrue(body.contains("24 hours"));
        assertTrue(body.contains("https://www.antweb.org/apiV3.1.do#terms"));
        assertTrue(body.contains("Working on a larger project? Brian L. Fisher welcomes collaborations; write to antweb@antweb.org."));
        assertFalse("no key in the email", body.contains("aw_"));

        ApiKeys.Registration r = new ApiKeys.Registration("Ana Silva", "ana@example.org", "", "Maps of Strumigenys", true);
        String copy = ApiKeyMgr.copyText(r, 42, LocalDateTime.of(2026, 11, 8, 14, 3), true, "https://www.antweb.org/apiKeys.do?id=42");
        for (String s : new String[] {"Ana Silva", "ana@example.org", "(none given)", "Maps of Strumigenys", "2026-11-08 14:03",
                                      "https://www.antweb.org/apiKeys.do?id=42", "number 42"}) {
            assertTrue(s, copy.contains(s));
        }
        assertFalse(copy.contains("token"));
        assertFalse(copy.contains("aw_"));
        assertTrue(ApiKeyMgr.copyText(r, 42, LocalDateTime.now(), false, "x").contains("could NOT be emailed"));
        assertEquals("bfisher@calacademy.org", ApiKeys.COPY_TO);
        for (String text : new String[] {body, copy, ApiKeysAction.HELP}) {
            assertFalse("no em-dash", text.contains("\u2014"));
        }
        assertTrue(ApiKeysAction.HELP.contains("5 requests a minute, 50 a day and 25 records an answer"));
        assertTrue(ApiKeysAction.HELP.contains("60 requests a minute, 5,000 a day, 500 records an answer and 50,000 records a day"));
    }

    public void testRegistrationOpensWithTheSettingsFile() {
        assertFalse(ApiKeys.isOpen(null));
        Properties p = new Properties();
        assertFalse(ApiKeys.isOpen(p));
        p.setProperty("apiKeys.open", "false");
        assertFalse(ApiKeys.isOpen(p));
        p.setProperty("apiKeys.open", " true ");
        assertTrue(ApiKeys.isOpen(p));
        // No file on a test machine: registration is not open.
        assertFalse(ApiKeys.isOpen());
    }
}

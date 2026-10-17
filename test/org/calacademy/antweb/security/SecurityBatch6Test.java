package test.org.calacademy.antweb.security;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import junit.framework.TestCase;
import org.calacademy.antweb.DescHistory;
import org.calacademy.antweb.curate.project.EditProjectForm;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;

/** Checks added with batch 6 (2026-10-17). */
public class SecurityBatch6Test extends TestCase {

    // ---------- DBUtil.sqlQuote: same SQL as the old '" + x + "' for plain values, escaped otherwise ----------

    public void testSqlQuote() {
        assertEquals("'formicidae'", DBUtil.sqlQuote("formicidae"));
        assertEquals("'camponotus maculatus'", DBUtil.sqlQuote("camponotus maculatus"));
        assertEquals("'x'' or ''1''=''1'", DBUtil.sqlQuote("x' or '1'='1"));
        assertEquals("'x\\\\'' or sleep(5) -- '", DBUtil.sqlQuote("x\\' or sleep(5) -- "));
        assertEquals("'null'", DBUtil.sqlQuote(null));       // as the old concatenation
        assertEquals("'42'", DBUtil.sqlQuote(42));
        assertEquals("'myrm%'", DBUtil.sqlQuote("myrm" + "%"));
    }

    // ---------- DescHistory.safePage: only the pages with page text, only their naming parameters ----------

    public void testSafePageKeepsPageTextPages() {
        assertEquals("description.do?genus=polyrhachis&species=amana&rank=species",
            DescHistory.safePage("description.do?genus=polyrhachis&species=amana&rank=species"));
        assertEquals("specimen.do?name=casent0922067", DescHistory.safePage("/specimen.do?name=casent0922067"));
        assertEquals("project.do?name=madagascarants", DescHistory.safePage("project.do?name=madagascarants"));
        assertEquals("country.do?name=Comoros", DescHistory.safePage("country.do?name=Comoros"));
        assertEquals("adm1.do?name=Toamasina&country=Madagascar", DescHistory.safePage("adm1.do?name=Toamasina&country=Madagascar"));
        assertEquals("museum.do?museumCode=CASC", DescHistory.safePage("museum.do?museumCode=CASC"));
        assertEquals("bioregion.do?name=Malagasy", DescHistory.safePage("bioregion.do?name=Malagasy"));
    }

    public void testSafePageRefusesOtherActions() {
        assertNull(DescHistory.safePage("saveGroup.do?step=delete&id=5"));
        assertNull(DescHistory.safePage("util.do?action=reloadAntwebMgr"));
        assertNull(DescHistory.safePage("editLogin.do?id=1"));
        assertNull(DescHistory.safePage("defaultSpecimen.do?command=post&taxonName=x"));
        assertNull(DescHistory.safePage("fieldHistory.do?field=comments"));
        assertNull(DescHistory.safePage("//other.example/description.do?genus=a"));
        assertNull(DescHistory.safePage("description.do"));              // names nothing
        assertNull(DescHistory.safePage("description.do?step=delete"));  // only unknown parameters
        assertNull(DescHistory.safePage(null));
    }

    public void testSafePageDropsOtherParameters() {
        assertEquals("project.do?name=x", DescHistory.safePage("project.do?name=x&action=recalc"));
        assertEquals("description.do?genus=a&rank=genus",
            DescHistory.safePage("description.do?genus=a&editField=comments&_csrf=t&isSaveEditField=true&restoreFrom=3&rank=genus&field=x"));
        assertEquals("specimen.do?name=c", DescHistory.safePage("specimen.do?name=c&command=delete"));
        assertTrue(DescHistory.isPageAction("description.do"));
        assertFalse(DescHistory.isPageAction("saveGroup.do"));
    }

    // ---------- EditProjectForm: plain-text settings carry no markup ----------

    public void testProjectPlainText() {
        assertEquals("/titlescriptalert(1)/script", EditProjectForm.plain("</title><script>alert(1)</script>"));
        assertEquals("Ants of Cote d'Ivoire", EditProjectForm.plain("Ants of Cote d'Ivoire"));
        assertNull(EditProjectForm.plain(null));
        EditProjectForm f = new EditProjectForm();
        f.setTitle("<b>Madagascar</b>");
        assertEquals("bMadagascar/b", f.getTitle());
    }

    // ---------- Csrf: a token in the URL counts only for multipart (upload) forms ----------

    private static final String TOKEN = "tok_abcdefghijklmnopqrstuvwxyz0123456789ABCD";

    /** A stand-in request: query string, content type, parameters (query values listed first, as Tomcat does). */
    private static HttpServletRequest request(String query, String contentType, Map<String, String[]> params) {
        HttpSession session = (HttpSession) Proxy.newProxyInstance(SecurityBatch6Test.class.getClassLoader(),
            new Class<?>[] {HttpSession.class}, (p, m, a) -> {
                if (m.getName().equals("getAttribute") && "antweb.csrfToken".equals(a[0])) return TOKEN;
                return null;
            });
        return (HttpServletRequest) Proxy.newProxyInstance(SecurityBatch6Test.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "getQueryString": return query;
                    case "getContentType": return contentType;
                    case "getSession": return session;
                    case "getServerName": return "staging.antweb.org";
                    case "getParameterValues": return params.get((String) a[0]);
                    case "getParameter": {
                        String[] v = params.get((String) a[0]);
                        return v == null || v.length == 0 ? null : v[0];
                    }
                    default: return null;
                }
            });
    }

    private static Map<String, String[]> params(String... kv) {
        Map<String, String[]> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            String[] old = m.get(kv[i]);
            String[] v = old == null ? new String[] {kv[i + 1]} : java.util.Arrays.copyOf(old, old.length + 1);
            v[v.length - 1] = kv[i + 1];
            m.put(kv[i], v);
        }
        return m;
    }

    public void testFormTokenInBody() {
        assertNull(Csrf.check(request("id=1", "application/x-www-form-urlencoded", params("id", "1", "_csrf", TOKEN))));
    }

    public void testFormTokenInUrlOnlyIsRefused() {
        assertEquals("missing token", Csrf.check(request("id=1&_csrf=" + TOKEN, "application/x-www-form-urlencoded",
            params("id", "1", "_csrf", TOKEN))));
        assertEquals("missing token", Csrf.check(request("%5Fcsrf=" + TOKEN, "application/x-www-form-urlencoded",
            params("_csrf", TOKEN))));
    }

    public void testFormTokenInUrlIgnoredWhenBodyHasIt() {
        // A token in the action URL is skipped; the body's token decides.
        assertNull(Csrf.check(request("_csrf=old", "application/x-www-form-urlencoded",
            params("_csrf", "old", "_csrf", TOKEN))));
        assertEquals("token mismatch", Csrf.check(request("_csrf=" + TOKEN, "application/x-www-form-urlencoded",
            params("_csrf", TOKEN, "_csrf", "wrong"))));
    }

    public void testMultipartTokenInUrl() {
        assertNull(Csrf.check(request("_csrf=" + TOKEN, "multipart/form-data; boundary=x", params())));
        assertEquals("missing token", Csrf.check(request("a=1", "multipart/form-data; boundary=x", params())));
    }

    public void testQueryCount() {
        assertEquals(0, Csrf.queryCount(null));
        assertEquals(0, Csrf.queryCount("a=1&x_csrf=2"));
        assertEquals(1, Csrf.queryCount("a=1&_csrf=2"));
        assertEquals(2, Csrf.queryCount("_csrf=1&%5Fcsrf=2"));
    }
}

package test.org.calacademy.antweb.audit;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.calacademy.antweb.Login;
import org.calacademy.antweb.curate.login.LoginEdit;
import org.calacademy.antweb.home.PasswordResetDb;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;

/**
 * 2026-10-28: logins, sessions and small request helpers.
 * - The sign-in pages refuse a post from another site (Origin or Referer of another host).
 * - Removing administrator or upload rights ends the login's sessions (LoginEdit.removesRights); a deleted
 *   login reads as session_version -1, so its sessions end.
 * - Project links on the curator pages are escaped.
 * - HttpUtil.verbatimify escapes; HttpUtil.isSecure does not fail without a target; DBUtil.removeConn
 *   accepts a connection that is not from the pool.
 */
public class LoginSessionFixesTest extends TestCase {

    private static HttpServletRequest request(String path, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(LoginSessionFixesTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getServletPath": return path;
                    case "getRequestURI": return path;
                    case "getServerName": return "www.antweb.org";
                    case "getMethod": return "POST";
                    case "getHeader": return headers.get((String) args[0]);
                    default: return null;
                }
            });
    }

    public void testSignInPostFromAnotherSiteIsRefused() {
        assertTrue(Csrf.isSignInPath(request("/login.do", Map.of())));
        assertTrue(Csrf.isSignInPath(request("/resetPassword.do", Map.of())));
        assertFalse(Csrf.isSignInPath(request("/saveLogin.do", Map.of())));
        assertFalse(Csrf.isSignInPath(request("/v3.1/specimens", Map.of())));

        assertEquals("cross-site Origin", Csrf.crossSite(request("/login.do", Map.of("Origin", "https://evil.example"))));
        assertEquals("cross-site Referer", Csrf.crossSite(request("/login.do", Map.of("Referer", "https://evil.example/x"))));
        assertNull(Csrf.crossSite(request("/login.do", Map.of("Origin", "https://www.antweb.org"))));
        assertNull(Csrf.crossSite(request("/login.do", Map.of("Referer", "https://www.antweb.org/login.do"))));
        assertNull("no header: not a browser form from another site", Csrf.crossSite(request("/login.do", Map.of())));
    }

    private static LoginEdit.State state(boolean admin, boolean specimens, boolean images) {
        LoginEdit.State s = new LoginEdit.State();
        s.isAdmin = admin;
        s.uploadSpecimens = specimens;
        s.uploadImages = images;
        return s;
    }

    public void testRemovedRightsEndSessions() {
        assertTrue(LoginEdit.removesRights(state(true, true, true), state(false, true, true)));
        assertTrue(LoginEdit.removesRights(state(false, true, true), state(false, false, true)));
        assertTrue(LoginEdit.removesRights(state(false, true, true), state(false, true, false)));
        assertFalse("added rights wait for the next login", LoginEdit.removesRights(state(false, false, false), state(true, true, true)));
        assertFalse(LoginEdit.removesRights(state(true, true, true), state(true, true, true)));
        assertFalse(LoginEdit.removesRights(null, state(false, false, false)));
        assertTrue("a deleted login's version never equals a stamp", PasswordResetDb.DELETED_LOGIN_VERSION < 0);
    }

    public void testProjectLinkEscaped() {
        String link = Login.projectLink("https://www.antweb.org", "x'y&z", "<b>Ants</b> & co");
        assertTrue(link, link.contains("&lt;b&gt;Ants&lt;/b&gt; &amp; co"));
        assertFalse(link, link.contains("<b>"));
        assertTrue(link, link.contains("name=x%27y%26z"));
        assertTrue(link, link.startsWith("<a href='https://www.antweb.org/project.do?name="));
    }

    public void testSmallHelpers() throws Exception {
        assertEquals("&lt;i&gt;x&lt;/i&gt;", HttpUtil.verbatimify("<i>x</i>"));
        assertNull(HttpUtil.verbatimify(null));
        // A request without a target (getTarget returns null): used to fail with a NullPointerException.
        assertNull(HttpUtil.getTarget(request("/academyHeader.jsp", Map.of())));
        assertTrue(HttpUtil.isSecure(request("/academyHeader.jsp", Map.of())));

        Connection plain = (Connection) Proxy.newProxyInstance(LoginSessionFixesTest.class.getClassLoader(),
            new Class<?>[] {Connection.class}, (proxy, m, args) -> null);
        Method remove = DBUtil.class.getDeclaredMethod("removeConn", Connection.class);
        remove.setAccessible(true);
        assertEquals(Boolean.FALSE, remove.invoke(null, plain));
    }
}

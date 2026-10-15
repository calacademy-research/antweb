package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.upload.DescEditImageUploadAction;
import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.LoginMgr;

/** Access checks added 2026-10-15. */
public class AccessChecksTest extends TestCase {

    private static Login login(int id, int groupId, boolean admin, String uploadAs) {
        Login l = new Login();
        l.setId(id);
        l.setGroupId(groupId);
        l.setIsAdmin(admin);
        l.setUploadAs(uploadAs);
        return l;
    }

    public void testAnonymousAndSelfRegisteredAreNotCurators() {
        assertFalse(LoginMgr.isCurator(LoginMgr.getAnonLogin()));
        assertFalse(LoginMgr.isCurator(login(500, -1, false, null)));
        assertTrue(LoginMgr.isCurator(login(501, 16, false, null)));
    }

    public void testUploadAs() {
        Login curator = login(10, 16, false, "20, 21");
        assertTrue(UploadAction.isAllowedUploadAs(curator, null));
        assertTrue(UploadAction.isAllowedUploadAs(curator, ""));
        assertTrue(UploadAction.isAllowedUploadAs(curator, "10"));
        assertTrue(UploadAction.isAllowedUploadAs(curator, "21"));
        assertFalse(UploadAction.isAllowedUploadAs(curator, "22"));
        assertTrue(UploadAction.isAllowedUploadAs(login(1, 1, true, null), "22"));
    }

    public void testUploadFileNames() {
        assertTrue(DescEditImageUploadAction.isSafeFileName("head_view.jpg", false));
        assertTrue(DescEditImageUploadAction.isSafeFileName("Key-2026.pdf", false));
        assertFalse(DescEditImageUploadAction.isSafeFileName("shell.jsp", false));
        assertFalse(DescEditImageUploadAction.isSafeFileName("shell.jsp", true));
        assertFalse(DescEditImageUploadAction.isSafeFileName("SHELL.JSPX", true));
        assertFalse(DescEditImageUploadAction.isSafeFileName("page.html", false));
        assertTrue(DescEditImageUploadAction.isSafeFileName("page.html", true));
        assertFalse(DescEditImageUploadAction.isSafeFileName("../x.jpg", false));
        assertFalse(DescEditImageUploadAction.isSafeFileName("a/b.jpg", false));
        assertFalse(DescEditImageUploadAction.isSafeFileName(".hidden.jpg", false));
        assertFalse(DescEditImageUploadAction.isSafeFileName("noext", true));
    }

    public void testUploadDirs() {
        assertTrue(DescEditImageUploadAction.isSafeDir("curator"));
        assertTrue(DescEditImageUploadAction.isSafeDir("worldAuthorityFiles"));
        assertFalse(DescEditImageUploadAction.isSafeDir("../WEB-INF"));
        assertFalse(DescEditImageUploadAction.isSafeDir("/etc"));
        assertFalse(DescEditImageUploadAction.isSafeDir("web\\log"));
    }

    public void testSecureCodeNotLogged() {
        assertEquals("https://x/utilData.do?action=a&secureCode=***&b=1",
            HttpUtil.forLog("https://x/utilData.do?action=a&secureCode=12345&b=1"));
        assertEquals("https://x/a.do?SECURECODE=***", HttpUtil.forLog("https://x/a.do?SECURECODE=9"));
    }
}

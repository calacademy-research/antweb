package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.upload.UploadFileAction;

/** Archived upload files: admins any, curators their own group's specimen files only. */
public class UploadFileAccessTest extends TestCase {

    private static Login login(int groupId, boolean admin) {
        Login l = new Login();
        l.setId(100);
        l.setGroupId(groupId);
        l.setIsAdmin(admin);
        return l;
    }

    public void testCuratorOwnGroupOnly() {
        Login curator2 = login(2, false);
        assertTrue(UploadFileAction.mayRead(curator2, "20220224-11:49:08-specimen2.txt"));
        assertTrue(UploadFileAction.mayRead(curator2, "20261018-05:31:02-specimenTW2.txt"));
        assertTrue(UploadFileAction.mayRead(curator2, "20261018-05:31:02-specimenGBIF2.txt"));
        assertFalse(UploadFileAction.mayRead(curator2, "20220224-11:49:08-specimen12.txt"));
        assertFalse(UploadFileAction.mayRead(curator2, "20220224-11:49:08-specimen21.txt"));
        assertFalse(UploadFileAction.mayRead(curator2, "20220224-11:49:08-specimen1.txt"));
        assertFalse(UploadFileAction.mayRead(curator2, "20220224-11:49:08-worldants_speciesList.txt"));
        assertFalse(UploadFileAction.mayRead(curator2, null));
    }

    public void testAdminAndOthers() {
        assertTrue(UploadFileAction.mayRead(login(1, true), "20220224-11:49:08-specimen2.txt"));
        assertTrue(UploadFileAction.mayRead(login(1, true), "20220224-11:49:08-worldants_speciesList.txt"));
        assertFalse(UploadFileAction.mayRead(null, "20220224-11:49:08-specimen2.txt"));
        // A login with no group (self-registered) is not a curator.
        assertFalse(UploadFileAction.mayRead(login(-1, false), "20220224-11:49:08-specimen-1.txt"));
        assertFalse(UploadFileAction.mayRead(login(0, false), "20220224-11:49:08-specimen0.txt"));
    }

    /** One line of an upload file (specimen page "Upload File Line"): any curator or admin, any group (2026-10-24). */
    public void testAnyCuratorReadsOneLineOfAnyGroup() {
        assertTrue(UploadFileAction.mayReadLine(login(2, false), "20261024-05:05:20-specimenTW1.txt"));
        assertTrue(UploadFileAction.mayReadLine(login(1, true), "20261024-05:05:20-specimenTW1.txt"));
        assertFalse(UploadFileAction.mayReadLine(login(0, false), "20261024-05:05:20-specimenTW1.txt"));   // not a curator
        assertFalse(UploadFileAction.mayReadLine(null, "20261024-05:05:20-specimenTW1.txt"));
        // The whole file stays with the group's own curators.
        assertFalse(UploadFileAction.mayRead(login(2, false), "20261024-05:05:20-specimenTW1.txt"));
    }
}

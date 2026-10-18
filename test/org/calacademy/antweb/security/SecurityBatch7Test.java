package test.org.calacademy.antweb.security;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

import junit.framework.TestCase;
import org.calacademy.antweb.Artist;
import org.calacademy.antweb.ArtistAction;
import org.calacademy.antweb.EditLoginAction;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.ImageDb;
import org.calacademy.antweb.imageUploader.ImageUploadBatchAction;
import org.calacademy.antweb.imageUploader.ImageUploaded;
import org.calacademy.antweb.search.SearchListAction;
import org.calacademy.antweb.upload.UploadAction;

/**
 * Checks added with the hardening batch of 2026-10-18. Runs in CI inside the antweb image
 * (.github/workflows/tests.yaml), with junit.textui.TestRunner.
 */
public class SecurityBatch7Test extends TestCase {

    // ---------- image replacement: own group only (admins: any) ----------

    /** ImageDb with fixed owner groups, no database. */
    private static ImageDb owners(Integer... groups) {
        return new ImageDb(null) {
            @Override
            public Set<Integer> getImageGroupIds(String code, String shotType, int shotNumber) {
                return new java.util.HashSet<>(java.util.Arrays.asList(groups));
            }
        };
    }

    private static Login login(int groupId, boolean admin) {
        Login l = new Login();
        l.setId(42);
        l.setGroupId(groupId);
        l.setIsAdmin(admin);
        return l;
    }

    private static ImageUploaded image() {
        ImageUploaded iu = new ImageUploaded();
        iu.init("CASENT0001234_H_1.jpg");
        return iu;
    }

    public void testNewImageMayBeUploaded() {
        assertTrue(ImageUploadBatchAction.mayReplace(image(), owners(), login(16, false)));
    }

    public void testOwnGroupMayReplace() {
        assertTrue(ImageUploadBatchAction.mayReplace(image(), owners(16), login(16, false)));
    }

    public void testOtherGroupMayNotReplace() {
        assertFalse(ImageUploadBatchAction.mayReplace(image(), owners(1), login(16, false)));
        assertFalse(ImageUploadBatchAction.mayReplace(image(), owners(1, 16), login(16, false)));
    }

    public void testAdminMayReplaceAny() {
        assertTrue(ImageUploadBatchAction.mayReplace(image(), owners(1), login(16, true)));
    }

    // ---------- backups are never overwritten ----------

    public void testBackupNamesAreNew() throws Exception {
        File dir = Files.createTempDirectory("imgbak").toFile();
        try {
            String first = ImageUploaded.backupName("CASENT0001234_H_1.tif", dir, "20261018-120000");
            assertEquals("CASENT0001234_H_1.20261018-120000.tif", first);
            assertTrue(new File(dir, first).createNewFile());
            String second = ImageUploaded.backupName("CASENT0001234_H_1.tif", dir, "20261018-120000");
            assertEquals("CASENT0001234_H_1.20261018-120000-2.tif", second);
            assertTrue(new File(dir, second).createNewFile());
            assertEquals("CASENT0001234_H_1.20261018-120000-3.tif",
                ImageUploaded.backupName("CASENT0001234_H_1.tif", dir, "20261018-120000"));
            String now = ImageUploaded.backupName("CASENT0001234_H_1.jpg", dir);
            assertTrue(now, now.matches("CASENT0001234_H_1\\.\\d{8}-\\d{6}\\.jpg"));
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    // ---------- own email change needs the current password ----------

    public void testEmailChanged() {
        assertFalse(EditLoginAction.emailChanged("a@b.org", "a@b.org"));
        assertFalse(EditLoginAction.emailChanged("A@B.org", " a@b.org "));
        assertFalse(EditLoginAction.emailChanged(null, ""));
        assertTrue(EditLoginAction.emailChanged("a@b.org", "c@d.org"));
        assertTrue(EditLoginAction.emailChanged(null, "c@d.org"));
        assertTrue(EditLoginAction.emailChanged("a@b.org", ""));
    }

    // ---------- upload.do site-wide jobs, artists, search downloads ----------

    public void testAdminOnlyUploadActions() {
        assertTrue(UploadAction.isAdminOnlyAction("genAll"));
        assertTrue(UploadAction.isAdminOnlyAction("allSpecimenFiles"));
        assertFalse(UploadAction.isAdminOnlyAction("specimenUpload"));
        assertFalse(UploadAction.isAdminOnlyAction(null));
    }

    public void testArtistChangesOwnerOrAdmin() {
        Artist a = new Artist(5, "Some Artist");
        a.setCuratorId(42);
        assertTrue(ArtistAction.mayChange(a, login(16, false)));   // login id 42
        Login other = login(16, false);
        other.setId(43);
        assertFalse(ArtistAction.mayChange(a, other));
        Login admin = login(1, true);
        admin.setId(44);
        assertTrue(ArtistAction.mayChange(a, admin));
        assertFalse(ArtistAction.mayChange(a, null));
        assertFalse(ArtistAction.mayChange(null, admin));
    }

    public void testOldSearchDownloadsRemoved() throws Exception {
        File dir = Files.createTempDirectory("search").toFile();
        File outside = Files.createTempFile("keep", "search.txt").toFile();
        try {
            File old = new File(dir, "20261001-120000-42search.txt");
            File fresh = new File(dir, "20261018-120000-42search.txt");
            File other = new File(dir, "notes.txt");
            File link = new File(dir, "link-search.txt");
            for (File f : new File[]{old, fresh, other}) assertTrue(f.createNewFile());
            Files.createSymbolicLink(link.toPath(), outside.toPath());
            long now = System.currentTimeMillis();
            old.setLastModified(now - 3L * 24 * 3600 * 1000);
            other.setLastModified(now - 3L * 24 * 3600 * 1000);
            outside.setLastModified(now - 3L * 24 * 3600 * 1000);
            assertEquals(1, SearchListAction.removeOldFiles(dir, now - 24L * 3600 * 1000));
            assertFalse(old.exists());
            assertTrue(fresh.exists());
            assertTrue(other.exists());
            assertTrue(Files.isSymbolicLink(link.toPath()));
            assertTrue(outside.exists());
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
            outside.delete();
        }
    }
}

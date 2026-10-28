package test.org.calacademy.antweb.security;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.calacademy.antweb.home.Copyright;
import org.calacademy.antweb.home.ImageDb;
import org.calacademy.antweb.home.ImagePickDb;
import org.calacademy.antweb.imageUploader.ImageUpload;
import org.calacademy.antweb.imageUploader.ImageUploaded;

/**
 * Image uploads and default images (2026-10-27), without a database:
 * - a replaced image keeps its old row when the new row cannot be written (one transaction), and the
 *   uploader is told it was not saved;
 * - file names with an unknown shot type or an image number below 1 are refused;
 * - the default image of a genus comes from that genus, not from a longer genus name with the same start.
 * Runs in CI inside the antweb image (.github/workflows/tests.yaml), with junit.textui.TestRunner.
 */
public class ImageUploadSaveTest extends TestCase {

    /** A connection that records what is done, and fails every statement containing failOn. */
    static final class FakeDb {
        final List<String> log = new ArrayList<>();
        boolean autoCommit = true;
        final String failOn;

        FakeDb(String failOn) { this.failOn = failOn; }

        Connection connection() {
            InvocationHandler rs = (p, m, a) -> {
                switch (m.getName()) {
                    case "next": return false;
                    case "close": return null;
                    default: return m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
                }
            };
            InvocationHandler stmt = (p, m, a) -> {
                String n = m.getName();
                if (n.equals("executeUpdate") || n.equals("executeQuery") || n.equals("execute")) {
                    String sql = (String) a[0];
                    log.add(sql);
                    if (failOn != null && sql.contains(failOn)) throw new SQLException("forced failure: " + failOn);
                    if (n.equals("executeQuery")) return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, rs);
                    if (n.equals("execute")) return false;
                    return sql.startsWith("delete") ? 1 : 1;
                }
                if (n.equals("close")) return null;
                return m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
            };
            InvocationHandler con = (p, m, a) -> {
                switch (m.getName()) {
                    case "createStatement": return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class}, stmt);
                    case "getAutoCommit": return autoCommit;
                    case "setAutoCommit": autoCommit = (Boolean) a[0]; log.add("autoCommit=" + autoCommit); return null;
                    case "commit": log.add("commit"); return null;
                    case "rollback": log.add("rollback"); return null;
                    case "isClosed": return false;
                    case "toString": return "FakeDb";
                    default: return m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
                }
            };
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, con);
        }
    }

    private static ImageUploaded upload(String name) {
        ImageUploaded iu = new ImageUploaded();
        assertEquals("success", iu.init(name));
        ImageUpload batch = new ImageUpload();
        batch.setId(7);
        batch.setGroupId(3);
        batch.setArtistId(5);
        batch.setCopyright(new Copyright(1, "California Academy of Sciences", 2026));
        iu.setImageUpload(batch);
        return iu;
    }

    public void testFailedInsertKeepsTheOldRow() {
        FakeDb db = new FakeDb("insert into image ");
        boolean saved = new ImageDb(db.connection()).putImage(upload("CASENT0001234_H_1.jpg"));
        assertFalse("putImage said saved although the insert failed", saved);
        assertTrue(db.log.toString(), db.log.contains("rollback"));
        assertFalse(db.log.toString(), db.log.contains("commit"));
        assertTrue("autocommit not restored: " + db.log, db.autoCommit);
        // The delete ran inside the transaction (autocommit off) and was rolled back.
        int off = db.log.indexOf("autoCommit=false");
        int del = -1;
        for (int i = 0; i < db.log.size(); i++) if (db.log.get(i).startsWith("delete from image")) del = i;
        assertTrue(db.log.toString(), off >= 0 && del > off);
    }

    public void testSavedImageIsCommittedAndReplacesEveryOldRow() {
        FakeDb db = new FakeDb(null);
        boolean saved = new ImageDb(db.connection()).putImage(upload("CASENT0001234_H_1.jpg"));
        assertTrue(saved);
        assertTrue(db.log.toString(), db.log.contains("commit"));
        assertFalse(db.log.toString(), db.log.contains("rollback"));
        assertTrue(db.autoCommit);
        // The old row is deleted whether or not it has a group (no "exists" test through group_image first).
        boolean deleteImage = false, deleteGroup = false;
        for (String s : db.log) {
            if (s.startsWith("delete from image where image_of_id = 'casent0001234' and shot_type='h' and shot_number=1")) deleteImage = true;
            if (s.startsWith("delete from group_image")) deleteGroup = true;
        }
        assertTrue(db.log.toString(), deleteImage && deleteGroup);
    }

    public void testFailedGroupRowRollsBackTheImageRow() {
        FakeDb db = new FakeDb("insert into group_image");
        assertFalse(new ImageDb(db.connection()).putImage(upload("CASENT0001234_P_2.tif")));
        assertTrue(db.log.toString(), db.log.contains("rollback"));
    }

    public void testFileNamesNeedAKnownShotAndANumberFromOne() {
        assertNull(ImageUploaded.checkName("CASENT0001234_H_2.jpg"));
        assertNull(ImageUploaded.checkName("CASENT0001234_D.jpg"));
        assertNotNull(ImageUploaded.checkName("CASENT0001234_ZZ_1.jpg"));
        assertNotNull(ImageUploaded.checkName("CASENT0001234_H_0.jpg"));
        assertNotNull(ImageUploaded.checkName("CASENT0001234_H_-1.jpg"));
        assertNotNull(ImageUploaded.checkName("CASENT0001234_Q.jpg"));
    }

    public void testGenusDefaultImageStaysInTheGenus() {
        String genus = ImagePickDb.higherTaxonClause("genus", "myrmicinaepheidole");
        assertTrue(genus, genus.contains("taxon_name = 'myrmicinaepheidole'"));
        assertTrue(genus, genus.contains("taxon_name like 'myrmicinaepheidole %'"));
        assertFalse(genus, genus.contains("'myrmicinaepheidole%'"));
        // A subfamily: every genus under it (genus names start with the subfamily's name).
        assertEquals(" taxon_name like 'myrmicinae%'", ImagePickDb.higherTaxonClause("subfamily", "myrmicinae"));
        // LIKE wildcards in a name are literal.
        assertTrue(ImagePickDb.higherTaxonClause("subfamily", "a_b").contains("a\\\\_b%"));
    }
}

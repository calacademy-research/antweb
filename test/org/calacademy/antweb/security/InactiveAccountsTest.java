package test.org.calacademy.antweb.security;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.calacademy.antweb.util.InactiveAccounts;
import org.calacademy.antweb.util.InactiveAccounts.Candidate;
import org.calacademy.antweb.util.PasswordResetMgr;

/** The rule that selects regular accounts not used for three years (InactiveAccounts). */
public class InactiveAccountsTest extends TestCase {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 12, 0);

    private static Candidate regular(int id, LocalDateTime lastLogin) {
        return new Candidate(id, lastLogin);
    }

    public void testCutoffIsThreeYears() {
        assertEquals(LocalDateTime.of(2023, 10, 18, 12, 0), InactiveAccounts.cutoff(NOW));
    }

    public void testLastUsedBeforeCutoff() {
        LocalDateTime cutoff = InactiveAccounts.cutoff(NOW);
        assertTrue(InactiveAccounts.qualifies(regular(10, cutoff.minusDays(1)), cutoff, 0));
        assertFalse(InactiveAccounts.qualifies(regular(10, cutoff.plusDays(1)), cutoff, 0));
        assertFalse(InactiveAccounts.qualifies(regular(10, cutoff), cutoff, 0));  // exactly three years: not yet
        assertFalse(InactiveAccounts.qualifies(regular(10, NOW.minusDays(2)), cutoff, 0));
    }

    public void testCuratorsAndAdminsNeverSelected() {
        LocalDateTime old = NOW.minusYears(10);
        LocalDateTime cutoff = InactiveAccounts.cutoff(NOW);
        Candidate admin = regular(1, old); admin.isAdmin = true;
        Candidate group = regular(2, old); group.groupId = 5;
        Candidate specimens = regular(3, old); specimens.uploadSpecimens = true;
        Candidate images = regular(4, old); images.uploadImages = true;
        Candidate projects = regular(5, old); projects.hasProjects = true;
        Candidate countries = regular(6, old); countries.hasCountries = true;
        for (Candidate c : new Candidate[] {admin, group, specimens, images, projects, countries}) {
            assertFalse("login " + c.id, InactiveAccounts.isRegular(c));
            assertFalse("login " + c.id, InactiveAccounts.qualifies(c, cutoff, 1000));
        }
        Candidate noGroup = regular(7, old); noGroup.groupId = 0;      // NULL group reads as 0
        Candidate minusOne = regular(8, old); minusOne.groupId = -1;   // web sign-ups
        assertTrue(InactiveAccounts.qualifies(noGroup, cutoff, 0));
        assertTrue(InactiveAccounts.qualifies(minusOne, cutoff, 0));
    }

    public void testAnonymousAndAlreadyInactiveSkipped() {
        LocalDateTime cutoff = InactiveAccounts.cutoff(NOW);
        assertFalse(InactiveAccounts.qualifies(regular(0, NOW.minusYears(9)), cutoff, 1000));
        assertFalse(InactiveAccounts.qualifies(regular(-1, null), cutoff, 1000));
        Candidate done = regular(12, NOW.minusYears(9)); done.inactive = true;
        assertFalse(InactiveAccounts.qualifies(done, cutoff, 1000));
    }

    public void testNeverLoggedInUsesIdOrder() {
        // Ids are given out in order. Login 200 last logged in four years ago, so every account
        // with a lower id existed then; a never-used account with a higher id may be new.
        List<Candidate> all = new ArrayList<>();
        all.add(regular(100, null));                        // older than 200: selected
        Candidate curator = regular(200, NOW.minusYears(4)); curator.groupId = 3;  // a curator still counts for the age
        all.add(curator);
        all.add(regular(250, NOW.minusMonths(2)));          // used recently: kept
        all.add(regular(300, null));                        // newer than 200: kept
        all.add(regular(150, NOW.minusYears(5)));           // used five years ago: selected
        LocalDateTime cutoff = InactiveAccounts.cutoff(NOW);
        assertEquals(200, InactiveAccounts.oldIdLimit(all, cutoff));

        List<Candidate> selected = InactiveAccounts.select(all, NOW);
        List<Integer> ids = new ArrayList<>();
        for (Candidate c : selected) ids.add(c.id);
        assertEquals(List.of(100, 150), ids);
    }

    public void testNoOldLoginsMeansNoNeverUsedSelected() {
        List<Candidate> all = new ArrayList<>();
        all.add(regular(5, null));
        all.add(regular(6, NOW.minusDays(10)));
        assertEquals(0, InactiveAccounts.oldIdLimit(all, InactiveAccounts.cutoff(NOW)));
        assertTrue(InactiveAccounts.select(all, NOW).isEmpty());
    }

    public void testEmailAndBackupName() {
        Candidate c = regular(9, null);
        assertFalse(c.hasEmail());
        c.email = "";
        assertFalse(c.hasEmail());
        c.email = "a@b.org";
        assertTrue(c.hasEmail());
        assertEquals("login_inactive_backup_20261018", InactiveAccounts.backupTableName(NOW));
    }

    public void testReactivationEmail() {
        String body = PasswordResetMgr.reactivationEmailBody("Ann <Lee>", "ann", "https://x/resetPassword.do?token=T");
        assertTrue(body.contains("Hello Ann &lt;Lee&gt;,"));
        assertTrue(body.contains("(username: ann) has not been used for more than three years"));
        assertTrue(body.contains("works for 24 hours, and only once"));
        assertTrue(body.contains("the account stays inactive"));
        assertFalse(body.contains("<Lee>"));
    }
}

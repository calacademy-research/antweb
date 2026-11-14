package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;

import org.calacademy.antweb.Status;

/**
 * AntCat statuses that AntWeb files as another status (Status.fromAntcat, read by the daily AntCat import in
 * SpeciesListUpload). AntCat #1617 (Brian Fisher, 2026-11-14): "unjustified emendation" (ICZN Art. 33.2.3) is a
 * junior objective synonym of the correctly spelled name; AntWeb stores and treats it as a synonym. No database.
 */
public class AntcatStatusTest extends TestCase {

    public void testAnUnjustifiedEmendationIsFiledAsASynonym() {
        assertEquals(Status.SYNONYM, Status.fromAntcat("unjustified emendation"));
        assertEquals(Status.SYNONYM, Status.fromAntcat(" Unjustified Emendation "));
        // so its specimens are filed under the current valid name, as a synonym's are
        assertTrue(Status.usesCurrentValidName(Status.fromAntcat("unjustified emendation")));
    }

    public void testEveryOtherStatusIsKeptAsItIs() {
        for (String s : Status.allStatusArray) assertSame(s, Status.fromAntcat(s));
        assertEquals("a status AntWeb does not know", Status.fromAntcat("a status AntWeb does not know"));
        assertEquals("", Status.fromAntcat(""));
        assertNull(Status.fromAntcat(null));
    }

    public void testTheNewStatusIsNotOneOfAntWebsOwn() {
        assertFalse(Status.getAllStatuses().contains(Status.UNJUSTIFIED_EMENDATION));
        assertEquals(1, Status.ANTCAT_STATUS_AS.size());
    }
}

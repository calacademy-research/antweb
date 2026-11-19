package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.UploadTimes;

/** UploadTimes (2026-11-18): the parts of the TaxonWorks load's time in compute.log. */
public class UploadTimesTest extends TestCase {

    public void testPartsInOrderSummedAndRounded() {
        UploadTimes t = new UploadTimes();
        t.add("delete", 31400);
        t.add("check", 200600);
        t.add("save", 999);
        t.add("check", 400);      // a part added again is summed
        t.add("negative", -5);    // never below 0
        assertEquals("delete 31 s, check 201 s, save 1 s, negative 0 s", t.line());
    }

    public void testLapsAndNothing() throws InterruptedException {
        UploadTimes t = new UploadTimes();
        assertEquals("", t.line());
        t.restart();
        Thread.sleep(20);
        t.lap("a");
        assertTrue(t.parts().get("a") >= 15);
        t.lap("b");
        assertTrue(t.parts().get("b") < 1000);
        assertEquals(2, t.parts().size());
    }
}

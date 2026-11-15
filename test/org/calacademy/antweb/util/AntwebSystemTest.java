package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.util.AntwebSystem;

/**
 * AntwebSystem.launchProcess (2026-11-15): the output of a command that ends at once is read whole. Until then the
 * output was read only while the process still ran, so a fast command could give nothing: the log viewer
 * (logMgr.do, which runs "tail") said "File empty or not found" for a log of 105 KB on production. Each command is
 * run many times, since the old code failed only now and then.
 */
public class AntwebSystemTest extends TestCase {

    public void testAFastCommandGivesItsOutputEveryTime() {
        AntwebSystem system = new AntwebSystem();
        for (int i = 0; i < 200; i++) {
            String out = system.launchProcess("echo antweb-line-" + i, true);
            assertEquals("run " + i, "<br>antweb-line-" + i, out);
        }
    }

    public void testAWholeFileIsReadByTail() throws Exception {
        java.io.File f = java.io.File.createTempFile("antweb-system-test", ".log");
        try {
            StringBuilder b = new StringBuilder();
            for (int i = 1; i <= 3000; i++) b.append("line ").append(i).append('\n');   // about 30 KB, under the pipe buffer
            java.nio.file.Files.write(f.toPath(), b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (int run = 0; run < 20; run++) {
                String out = new AntwebSystem().launchProcess("tail --lines 2000 " + f.getAbsolutePath(), true);
                assertTrue("run " + run, out.startsWith("<br>line 1001<br>line 1002") && out.endsWith("<br>line 3000"));
                assertEquals("run " + run, 2000, out.split("<br>", -1).length - 1);
            }
        } finally {
            f.delete();
        }
    }
}

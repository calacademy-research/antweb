package org.calacademy.antweb.upload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seconds spent in each part of a specimen upload (daily update stage two, 2026-11-18): the TaxonWorks load of the
 * daily update took about 20 minutes for 267,000 CAS records in one block, and whether sending records to the database
 * in batches would help depends on where those minutes go. SpecimenUpload.importSpecimens and TaxonWorksNightly add
 * their parts; TaxonWorksNightly writes line() into compute.log. Parts keep the order they were first added; adding to
 * a part again sums. Not thread-safe: one upload at a time. Tests: UploadTimesTest.
 */
public final class UploadTimes {

    private final Map<String, Long> millis = new LinkedHashMap<>();
    private long since = System.currentTimeMillis();

    /** Adds the time since the last lap (or the start) to the part, and starts the next lap now. */
    public void lap(String part) {
        long now = System.currentTimeMillis();
        add(part, now - since);
        since = now;
    }

    /** Adds ms to the part; does not move the lap. */
    public void add(String part, long ms) {
        millis.merge(part, Math.max(0, ms), Long::sum);
    }

    /** Starts the next lap now (time before it is not counted in any part). */
    public void restart() {
        since = System.currentTimeMillis();
    }

    public Map<String, Long> parts() {
        return millis;
    }

    /** "delete 31 s, read the file 12 s, check 201 s, ..." (whole seconds, rounded); "" when nothing was timed. */
    public String line() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Long> e : millis.entrySet()) {
            if (b.length() > 0) b.append(", ");
            b.append(e.getKey()).append(' ').append((e.getValue() + 500) / 1000).append(" s");
        }
        return b.toString();
    }
}

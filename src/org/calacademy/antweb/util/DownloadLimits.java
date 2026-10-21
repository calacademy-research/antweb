package org.calacademy.antweb.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.text.NumberFormat;
import java.util.Locale;

import org.calacademy.antweb.Rank;

/**
 * Limits of the specimen Download Data files (Brian Fisher, 2026-10-21): no free bulk downloads of
 * specimen data. A taxon page gives a file for a genus, species or subspecies only, and a file holds
 * at most MAX_RECORDS specimens (administrators: no cap). Larger data sets will go through the API
 * with registered tokens. Used by getSpecimenList.do (SpecimenListAction) and getSearchList.do
 * (SearchListAction). See docs/guides/downloads.md.
 */
public final class DownloadLimits {

    private DownloadLimits() {}

    public static final int MAX_RECORDS = 5000;

    public static final String CONTACT = "For larger data sets, contact antweb@antweb.org about API access.";

    public static final String RANK_REFUSED = "Downloads are available for a genus or species. " + CONTACT;

    /** Download Data of a taxon page: genus, species and subspecies only. */
    public static boolean isDownloadRank(String rank) {
        return Rank.GENUS.equals(rank) || Rank.SPECIES.equals(rank) || Rank.SUBSPECIES.equals(rank);
    }

    /** Most records a file may hold for this user (0: no cap, administrators). */
    public static int capFor(boolean isAdmin) {
        return isAdmin ? 0 : MAX_RECORDS;
    }

    /** True when a file with this many matching records is cut at the cap. */
    public static boolean isCapped(int matching, int cap) {
        return cap > 0 && matching > cap;
    }

    /** "This download is limited to 5,000 records (35,851 match). For larger ..." */
    public static String capMessage(int matching) {
        return "This download is limited to " + count(MAX_RECORDS) + " records (" + count(matching) + " match). " + CONTACT;
    }

    /** 141728 as "141,728". */
    public static String count(int n) {
        return NumberFormat.getIntegerInstance(Locale.US).format(n);
    }

    /** Deletes regular files (never links) in dir whose names end with suffix, older than the cutoff. */
    public static int removeOldFiles(File dir, String suffix, long cutoffMillis) {
        int removed = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (!f.getName().endsWith(suffix)) continue;
            if (!Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) continue;
            if (f.lastModified() >= cutoffMillis) continue;
            try {
                Files.delete(f.toPath());
                removed++;
            } catch (IOException e) {
                // left for the next run
            }
        }
        return removed;
    }
}

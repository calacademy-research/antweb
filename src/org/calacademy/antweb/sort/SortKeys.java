package org.calacademy.antweb.sort;

import org.calacademy.antweb.*;

/**
 * Null-safe keys for the SortTaxaBy comparators (2026-10-29). Before, each comparator caught a
 * NullPointerException and answered "equal", which breaks the comparator contract: a list with a few
 * missing values was not sorted, or the sort threw "Comparison method violates its general contract".
 * Missing values (no taxon set, no text, not a specimen) now always sort last.
 */
final class SortKeys {

    private SortKeys() {}

    /** Larger counts first; a missing count last. */
    static int countDesc(Integer a, Integer b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return Integer.compare(b, a);
    }

    /** Text in order, ignoring case; missing or blank text last. */
    static int textAsc(String a, String b) {
        boolean aNone = a == null || a.isBlank();
        boolean bNone = b == null || b.isBlank();
        if (aNone) return bNone ? 0 : 1;
        if (bNone) return -1;
        int c = a.trim().compareToIgnoreCase(b.trim());
        return c != 0 ? c : a.compareTo(b);
    }

    /** Text in reverse order (exactly as before, case included); missing text last. */
    static int textDesc(String a, String b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    static TaxonSet set(Taxon t) {
        return t == null ? null : t.getTaxonSet();
    }

    static Specimen specimen(Taxon t) {
        return t instanceof Specimen ? (Specimen) t : null;
    }
}

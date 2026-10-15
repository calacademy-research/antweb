package org.calacademy.antweb.curate.specimen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpecimenListLookupResult {
    private static final int MAX_DISPLAYED_ERRORS = 100;

    private final List<String> errors = new ArrayList<>();
    private int errorCount;
    private int rowCount;

    // Codes not in AntWeb (GitHub #64): not errors, never capped. One entry per
    // distinct code (case-insensitive), as typed in the file, in file order.
    private final List<String> notFoundCodes = new ArrayList<>();
    private int foundRowCount;

    // Lines with a space or tab inside the code: not looked up, listed as typed (file order).
    private final List<String> invalidCodes = new ArrayList<>();

    /** False only for problems with the file itself (name, size, whitespace, empty, too many codes). */
    public boolean isSuccess() {
        return errorCount == 0;
    }

    public void addError(String error) {
        errorCount++;
        if (errors.size() < MAX_DISPLAYED_ERRORS) errors.add(error);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public int getErrorCount() {
        return errorCount;
    }

    /** Number of specimen codes submitted (non-blank lines). */
    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public boolean hasAdditionalErrors() {
        return errorCount > errors.size();
    }

    void addNotFoundCode(String code) {
        notFoundCodes.add(code);
    }

    public List<String> getNotFoundCodes() {
        return Collections.unmodifiableList(notFoundCodes);
    }

    public boolean hasNotFoundCodes() {
        return !notFoundCodes.isEmpty();
    }

    void addInvalidCode(String code) {
        invalidCodes.add(code);
    }

    public List<String> getInvalidCodes() {
        return Collections.unmodifiableList(invalidCodes);
    }

    public boolean hasInvalidCodes() {
        return !invalidCodes.isEmpty();
    }

    public String getInvalidText() {
        return String.join("\n", invalidCodes);
    }

    /** All non-blank lines in the file, including codes with spaces. */
    public int getSubmittedCount() {
        return rowCount + invalidCodes.size();
    }

    /** True when the results page is needed instead of a direct download. */
    public boolean hasProblems() {
        return hasNotFoundCodes() || hasInvalidCodes();
    }

    /** The not-found codes, one per line, for copying into a file. */
    public String getNotFoundText() {
        return String.join("\n", notFoundCodes);
    }

    /** Rows written to the TSV: one per submitted line whose code is in AntWeb. */
    public int getFoundRowCount() {
        return foundRowCount;
    }

    void setFoundRowCount(int foundRowCount) {
        this.foundRowCount = foundRowCount;
    }

    /** Submitted lines whose code is not in AntWeb (duplicates in the file counted each time). */
    public int getNotFoundRowCount() {
        return rowCount - foundRowCount;
    }

    public static String escapeHtml(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '&': out.append("&amp;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }
}

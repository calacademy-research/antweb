package org.calacademy.antweb.curate.speciesList;

public final class ValidateSpeciesResultItem {
    public enum Status { EXACT_MATCH, NOT_FOUND, FORMAT_ERROR, AMBIGUOUS }

    public static final String CATEGORY_EXACT_MATCH = "Exact match";
    public static final String CATEGORY_SPELLING = "Spelling";
    public static final String CATEGORY_JUNIOR_SYNONYM = "Junior synonym";
    public static final String CATEGORY_COMBINATION_CHANGED = "Combination changed";
    public static final String CATEGORY_KNOWN_NOT_VALID = "Known but not valid";
    public static final String CATEGORY_NOT_FOUND = "Not found";
    public static final String CATEGORY_FORMAT_ERROR = "Format error";

    private final int rowNum;
    private final String inputRaw;
    private final String normalizedName;
    private final Status status;
    private final String lookupStatus;
    private final boolean hasSuggestedValidName;
    private final String category;
    private final boolean fossil;
    private final String message;
    private final String suggestion;

    public ValidateSpeciesResultItem(int rowNum, String inputRaw, String normalizedName, Status status, String message, String suggestion) {
        this(rowNum, inputRaw, normalizedName, status, "", false, message, suggestion, false);
    }

    public ValidateSpeciesResultItem(int rowNum, String inputRaw, String normalizedName, Status status,
            String lookupStatus, boolean hasSuggestedValidName, String message, String suggestion, boolean fossil) {
        this.rowNum = rowNum;
        this.inputRaw = inputRaw != null ? inputRaw : "";
        this.normalizedName = normalizedName != null ? normalizedName : "";
        this.status = status;
        this.lookupStatus = lookupStatus != null ? lookupStatus : "";
        this.hasSuggestedValidName = hasSuggestedValidName;
        this.category = computeCategory(status, this.lookupStatus, hasSuggestedValidName);
        this.fossil = fossil;
        this.message = message != null ? message : "";
        this.suggestion = suggestion != null ? suggestion : "";
    }

    public int getRowNum() { return rowNum; }
    public String getInputRaw() { return inputRaw; }
    public String getNormalizedName() { return normalizedName; }
    public Status getStatus() { return status; }
    public String getLookupStatus() { return lookupStatus; }
    public boolean hasSuggestedValidName() { return hasSuggestedValidName; }
    public String getCategory() { return category; }
    public boolean isNeedAttention() {
        return CATEGORY_SPELLING.equals(category)
                || CATEGORY_JUNIOR_SYNONYM.equals(category)
                || CATEGORY_COMBINATION_CHANGED.equals(category)
                || CATEGORY_NOT_FOUND.equals(category);
    }
    public boolean isFossil() { return fossil; }
    public String getFossilDisplay() { return fossil ? "yes" : "no"; }
    public String getMessage() { return message; }
    public String getSuggestion() { return suggestion; }

    private static String computeCategory(Status status, String lookupStatus, boolean hasSuggestedValidName) {
        if (status == Status.FORMAT_ERROR) {
            return CATEGORY_FORMAT_ERROR;
        }
        if ("valid".equals(lookupStatus) || status == Status.EXACT_MATCH) {
            return CATEGORY_EXACT_MATCH;
        }
        if ("unavailable misspelling".equals(lookupStatus)) {
            return CATEGORY_SPELLING;
        }
        if (status == Status.NOT_FOUND) {
            return hasSuggestedValidName ? CATEGORY_SPELLING : CATEGORY_NOT_FOUND;
        }
        if ("synonym".equals(lookupStatus)) {
            return CATEGORY_JUNIOR_SYNONYM;
        }
        if ("obsolete combination".equals(lookupStatus)) {
            return CATEGORY_COMBINATION_CHANGED;
        }
        if ("unidentifiable".equals(lookupStatus)
                || "unavailable".equals(lookupStatus)
                || "unrecognized".equals(lookupStatus)
                || "homonym".equals(lookupStatus)) {
            return CATEGORY_KNOWN_NOT_VALID;
        }
        return CATEGORY_KNOWN_NOT_VALID;
    }
}

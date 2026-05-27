package org.calacademy.antweb.curate.speciesList;

public final class ValidateSpeciesResultItem {
    public enum Status { EXACT_MATCH, NOT_FOUND, FORMAT_ERROR, AMBIGUOUS }

    private final int rowNum;
    private final String inputRaw;
    private final String normalizedName;
    private final Status status;
    private final String message;
    private final String suggestion;

    public ValidateSpeciesResultItem(int rowNum, String inputRaw, String normalizedName, Status status, String message, String suggestion) {
        this.rowNum = rowNum;
        this.inputRaw = inputRaw != null ? inputRaw : "";
        this.normalizedName = normalizedName != null ? normalizedName : "";
        this.status = status;
        this.message = message != null ? message : "";
        this.suggestion = suggestion != null ? suggestion : "";
    }

    public int getRowNum() { return rowNum; }
    public String getInputRaw() { return inputRaw; }
    public String getNormalizedName() { return normalizedName; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public String getSuggestion() { return suggestion; }
}

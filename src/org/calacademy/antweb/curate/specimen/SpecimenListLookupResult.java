package org.calacademy.antweb.curate.specimen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpecimenListLookupResult {
    private static final int MAX_DISPLAYED_ERRORS = 100;

    private final List<String> errors = new ArrayList<>();
    private int errorCount;
    private int rowCount;

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

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public boolean hasAdditionalErrors() {
        return errorCount > errors.size();
    }
}

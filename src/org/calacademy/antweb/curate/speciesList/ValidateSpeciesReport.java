package org.calacademy.antweb.curate.speciesList;

import java.util.ArrayList;
import java.util.List;

public class ValidateSpeciesReport {
    private final List<ValidateSpeciesResultItem> exactMatches = new ArrayList<>();
    private final List<ValidateSpeciesResultItem> problems = new ArrayList<>();
    private final List<ValidateSpeciesResultItem> formatErrors = new ArrayList<>();
    
    // For "Show Unmatched" feature
    private final List<String> unmatchedValidTaxa = new ArrayList<>();

    private int totalInputRows = 0;
    
    // Limits
    private boolean rowLimitExceeded = false;

    public void addResult(ValidateSpeciesResultItem item) {
        totalInputRows++;
        if (item.getStatus() == ValidateSpeciesResultItem.Status.EXACT_MATCH) {
            exactMatches.add(item);
        } else if (item.getStatus() == ValidateSpeciesResultItem.Status.FORMAT_ERROR) {
            formatErrors.add(item);
        } else {
            problems.add(item);
        }
    }

    public void addUnmatchedValidTaxon(String taxonName) {
        this.unmatchedValidTaxa.add(taxonName);
    }

    public List<ValidateSpeciesResultItem> getExactMatches() { return exactMatches; }
    public List<ValidateSpeciesResultItem> getProblems() { return problems; }
    public List<ValidateSpeciesResultItem> getFormatErrors() { return formatErrors; }
    public List<String> getUnmatchedValidTaxa() { return unmatchedValidTaxa; }
    
    public int getTotalInputRows() { return totalInputRows; }
    public int getExactMatchCount() { return exactMatches.size(); }
    public int getProblemCount() { return problems.size(); }
    public int getFormatErrorCount() { return formatErrors.size(); }
    public int getUnmatchedCount() { return unmatchedValidTaxa.size(); }

    public void setRowLimitExceeded(boolean exceeded) { this.rowLimitExceeded = exceeded; }
    public boolean isRowLimitExceeded() { return rowLimitExceeded; }

    public String generateTsvReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Row\tInput Raw\tNormalized Taxon Name\tStatus\tMessage\tSuggestion\n");
        
        List<ValidateSpeciesResultItem> all = new ArrayList<>();
        all.addAll(formatErrors);
        all.addAll(problems);
        all.addAll(exactMatches);
        
        // Sort by row number
        all.sort((a, b) -> Integer.compare(a.getRowNum(), b.getRowNum()));
        
        for (ValidateSpeciesResultItem item : all) {
            // Sanitize rawLine: replace embedded tabs to avoid corrupting TSV columns
            String safeRaw = item.getInputRaw().replace("\t", "  ");
            sb.append(item.getRowNum()).append("\t")
              .append(safeRaw).append("\t")
              .append(item.getNormalizedName()).append("\t")
              .append(item.getStatus().name()).append("\t")
              .append(item.getMessage()).append("\t")
              .append(item.getSuggestion()).append("\n");
        }

        if (!unmatchedValidTaxa.isEmpty()) {
            sb.append("\n\n--- UNMATCHED VALID ANTWEB TAXA ---\n");
            sb.append("Taxon Name\n");
            for (String t : unmatchedValidTaxa) {
                sb.append(t).append("\n");
            }
        }

        return sb.toString();
    }
    
    public String generateCorrectedTsvReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("taxon_name\n");
        
        List<ValidateSpeciesResultItem> all = new ArrayList<>();
        all.addAll(formatErrors);
        all.addAll(problems);
        all.addAll(exactMatches);
        
        // Sort by row number
        all.sort((a, b) -> Integer.compare(a.getRowNum(), b.getRowNum()));
        
        for (ValidateSpeciesResultItem item : all) {
            String taxonStr = "";
            if (item.getStatus() == ValidateSpeciesResultItem.Status.EXACT_MATCH) {
                taxonStr = item.getNormalizedName();
            } else if (item.getSuggestion() != null && !item.getSuggestion().isEmpty()
                       && Character.isUpperCase(item.getSuggestion().charAt(0))) {
                // Only include suggestions that look like actual taxon names (start with uppercase genus).
                // Excludes messages like "Check spelling.", "No valid species found...", etc.
                taxonStr = item.getSuggestion();
            }
            if (!taxonStr.isEmpty()) {
                sb.append(taxonStr).append("\n");
            }
        }
        return sb.toString();
    }
}

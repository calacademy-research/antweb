package org.calacademy.antweb.curate.speciesList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.calacademy.antweb.ValidationParseException;

public class SpeciesListValidator {

    private static final Log s_log = LogFactory.getLog(SpeciesListValidator.class);
    private final Connection connection;

    public SpeciesListValidator(Connection connection) {
        this.connection = connection;
    }

    public ValidateSpeciesReport validate(InputStream inputStream, String charSet, boolean showUnmatched) 
            throws ValidationParseException, SQLException, IOException {

        ValidateSpeciesReport report = new ValidateSpeciesReport();
        List<ParsedRow> rows = parseStream(inputStream, charSet);
        
        Set<String> matchedTaxonNames = new HashSet<>();
        
        for (ParsedRow row : rows) {
            if (row.hasError) {
                report.addResult(new ValidateSpeciesResultItem(
                        row.rowNum, row.rawLine, "", ValidateSpeciesResultItem.Status.FORMAT_ERROR, row.errorMsg, ""));
                continue;
            }

            // Normalization
            String subfamily = normalize(row.subfamily);
            String genus = normalizeCapitalized(row.genus);
            String species = normalize(row.species);
            String subspecies = normalize(row.subspecies);

            if (genus == null || species == null) {
                report.addResult(new ValidateSpeciesResultItem(
                    row.rowNum, row.rawLine, "", ValidateSpeciesResultItem.Status.FORMAT_ERROR, "Genus and species are required.", ""));
                continue;
            }

            // Human-readable display name: "Genus species [subspecies]"
            String displayName = genus + " " + species;
            if (subspecies != null) displayName += " " + subspecies;

            // Resolve the internal DB taxon_name key.
            // DB taxon_name is entirely lowercase (e.g. "dorylinaeaenictus clavatus atripennis").
            // When subfamily is known: key = subfamily + genus(lower) + " " + species [+ " " + subspecies]
            // When subfamily is unknown: query the DB by genus+species columns to get the key.
            String taxonName = null;
            String genusLower = genus.toLowerCase();
            if (subfamily != null) {
                taxonName = subfamily + genusLower + " " + species;
                if (subspecies != null) taxonName += " " + subspecies;
            } else {
                taxonName = lookupTaxonName(genusLower, species, subspecies);
            }

            // Lean DB lookup — only status + current_valid_name (avoids expensive image/bioregion queries)
            TaxonLookupResult result = taxonName != null ? lookupTaxon(taxonName) : null;
            
            if (result != null) {
                if ("valid".equals(result.status)) {
                    report.addResult(new ValidateSpeciesResultItem(
                            row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.EXACT_MATCH, "Exact match.", ""));
                    matchedTaxonNames.add(taxonName);
                } else if ("fossil".equals(result.status)) {
                    report.addResult(new ValidateSpeciesResultItem(
                            row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.NOT_FOUND, "Fossil taxon — not an extant valid name.", ""));
                } else if ("synonym".equals(result.status) || "homonym".equals(result.status)) {
                    // current_valid_name is an internal DB key (all lowercase, subfamily-prefixed).
                    // Resolve it to a human-readable display name.
                    String suggestion = resolveDisplayName(result.currentValidName);
                    report.addResult(new ValidateSpeciesResultItem(
                            row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.AMBIGUOUS, 
                            "Matched a " + result.status + ".", suggestion != null ? suggestion : ""));
                } else {
                    report.addResult(new ValidateSpeciesResultItem(
                            row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.AMBIGUOUS, 
                            "Status is '" + result.status + "'. Expected 'valid'.", ""));
                }
            } else {
                // Fuzzy match using display names for accurate distance calculation
                String suggestion = getBestFuzzyMatch(displayName, genus);
                report.addResult(new ValidateSpeciesResultItem(
                        row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.NOT_FOUND, "Taxon not found.", suggestion));
            }
        }

        if (showUnmatched) {
            populateUnmatched(report, matchedTaxonNames);
        }

        return report;
    }

    /**
     * Minimal status-only DB lookup. Avoids TaxonDb.getTaxon() which fires
     * expensive image / bioregion queries for every row in production.
     */
    private TaxonLookupResult lookupTaxon(String taxonName) throws SQLException {
        String q = "SELECT status, current_valid_name FROM taxon WHERE taxon_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(q)) {
            stmt.setString(1, taxonName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new TaxonLookupResult(rs.getString("status"), rs.getString("current_valid_name"));
                }
            }
        }
        return null;
    }

    /**
     * When no subfamily is provided (Format B without subfamily column),
     * look up the taxon_name by querying genus + species + subspecies columns.
     */
    private String lookupTaxonName(String genus, String species, String subspecies) throws SQLException {
        String subspClause = subspecies != null ? " AND subspecies = ?" : " AND (subspecies IS NULL OR subspecies = '')";
        String q = "SELECT taxon_name FROM taxon WHERE genus = ? AND species = ?" + subspClause
                 + " AND status != 'synonym' AND taxarank IN ('species','subspecies') LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(q)) {
            stmt.setString(1, genus.toLowerCase());
            stmt.setString(2, species);
            if (subspecies != null) stmt.setString(3, subspecies);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("taxon_name");
            }
        } catch (SQLException e) {
            s_log.warn("lookupTaxonName() genus:" + genus + " e:" + e.getMessage());
        }
        return null;
    }

    /**
     * Resolves an internal DB taxon_name key to a human-readable display name
     * by querying the DB for its genus/species/subspecies components.
     * Returns "Genus species [subspecies]" or null if not found.
     */
    private String resolveDisplayName(String internalTaxonName) {
        if (internalTaxonName == null || internalTaxonName.isEmpty()) return null;
        String q = "SELECT genus, species, subspecies FROM taxon WHERE taxon_name = ? LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(q)) {
            stmt.setString(1, internalTaxonName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return buildDisplayName(rs.getString("genus"), rs.getString("species"), rs.getString("subspecies"));
                }
            }
        } catch (SQLException e) {
            s_log.warn("resolveDisplayName() failed for: " + internalTaxonName + " e:" + e.getMessage());
        }
        return internalTaxonName; // fallback: return raw key
    }

    /**
     * Builds a human-readable display name from DB components.
     * Capitalizes genus, returns "Genus species [subspecies]".
     */
    private String buildDisplayName(String genus, String species, String subspecies) {
        if (genus == null || genus.isEmpty()) return null;
        String g = genus.substring(0, 1).toUpperCase() + genus.substring(1);
        StringBuilder sb = new StringBuilder(g);
        if (species != null && !species.isEmpty()) {
            sb.append(" ").append(species);
        }
        if (subspecies != null && !subspecies.isEmpty()) {
            sb.append(" ").append(subspecies);
        }
        return sb.toString();
    }

    private static class TaxonLookupResult {
        final String status;
        final String currentValidName;
        TaxonLookupResult(String status, String currentValidName) {
            this.status = status;
            this.currentValidName = currentValidName;
        }
    }

    private String normalize(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return s.trim().toLowerCase();
    }
    
    private String normalizeCapitalized(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        s = s.trim().toLowerCase();
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
    
    // Limits fuzzy matching to species sharing the same genus for performance.
    // Queries genus/species/subspecies columns to build proper display names for comparison.
    private String getBestFuzzyMatch(String displayName, String genus) {
        if (genus == null) return "Check spelling.";
        
        List<String> candidates = new ArrayList<>();
        // Query individual columns so we can build proper display names
        String query = "SELECT genus, species, subspecies FROM taxon WHERE genus = ? AND status = 'valid'";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, genus.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String candidate = buildDisplayName(rs.getString("genus"), rs.getString("species"), rs.getString("subspecies"));
                    if (candidate != null) candidates.add(candidate);
                }
            }
        } catch (SQLException e) {
            s_log.warn("Fuzzy match query failed: " + e.getMessage());
            return "Check spelling. (DB error)";
        }
        
        if (candidates.isEmpty()) return "No valid species found for genus " + genus + ".";
        
        int bestDistance = Integer.MAX_VALUE;
        String bestMatch = null;
        LevenshteinDistance ld = new LevenshteinDistance();
        
        for (String candidate : candidates) {
            int dist = ld.apply(displayName, candidate);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestMatch = candidate;
            }
        }
        
        if (bestDistance <= 4 && bestMatch != null) {
            return bestMatch;
        }
        
        return "Check spelling.";
    }
    
    private void populateUnmatched(ValidateSpeciesReport report, Set<String> matchedTaxa) {
        String q = "select taxon_name from taxon where status = 'valid' and taxarank in ('species', 'subspecies') order by taxon_name";
        try (PreparedStatement stmt = connection.prepareStatement(q);
             ResultSet rs = stmt.executeQuery()) {
            while(rs.next()) {
                String n = rs.getString(1);
                if (!matchedTaxa.contains(n)) {
                    report.addUnmatchedValidTaxon(n);
                }
            }
        } catch (SQLException e) {
            s_log.warn("Failed retrieving unmatched: " + e.getMessage());
        }
    }

    private List<ParsedRow> parseStream(InputStream is, String charset) throws ValidationParseException, IOException {
        List<ParsedRow> results = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset));
        
        String headerLine = reader.readLine();
        if (headerLine == null) throw new ValidationParseException("File is empty.");
        
        // Handle BOM
        if (headerLine.startsWith("\uFEFF")) {
            headerLine = headerLine.substring(1);
        }
        
        String[] headers = headerLine.toLowerCase().replace("\r", "").split("\t");
        boolean isOptionA = false;
        boolean isOptionB = false;
        
        int subfamIdx = -1, genusIdx = -1, speciesIdx = -1, subspIdx = -1, taxonNameIdx = -1;
        
        for (int i=0; i<headers.length; i++) {
            String h = headers[i].trim();
            if ("subfamily".equals(h)) subfamIdx = i;
            else if ("genus".equals(h)) genusIdx = i;
            else if ("species".equals(h)) speciesIdx = i;
            else if ("subspecies".equals(h)) subspIdx = i;
            else if ("taxon_name".equals(h)) taxonNameIdx = i;
        }
        
        if (genusIdx != -1 && speciesIdx != -1) isOptionA = true;
        else if (taxonNameIdx != -1) isOptionB = true;
        
        if (!isOptionA && !isOptionB) {
            throw new ValidationParseException("Unrecognized headers. File must contain either: ['genus', 'species'] or ['taxon_name'].");
        }
        
        int rowNum = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            rowNum++;
            line = line.replace("\r", "");
            if (line.trim().isEmpty()) continue; // skip blank lines
            
            if (rowNum > 50000) {
                throw new ValidationParseException("Maximum row limit of 50,000 exceeded. Stopping parse.");
            }
            
            ParsedRow pr = new ParsedRow();
            pr.rowNum = rowNum;
            pr.rawLine = line;
            
            String[] tokens = line.split("\t", -1);
            pr.subfamily = safeGet(tokens, subfamIdx);
            
            if (isOptionA) {
                pr.genus = safeGet(tokens, genusIdx);
                pr.species = safeGet(tokens, speciesIdx);
                pr.subspecies = safeGet(tokens, subspIdx);
                
                if (pr.genus == null || pr.species == null) {
                    pr.hasError = true;
                    pr.errorMsg = "Missing genus or species token.";
                }
            } else {
                String taxNameRaw = safeGet(tokens, taxonNameIdx);
                if (taxNameRaw == null || taxNameRaw.trim().isEmpty()) {
                    pr.hasError = true; pr.errorMsg = "Empty taxon_name.";
                } else {
                    String[] nameParts = taxNameRaw.trim().split("\\s+");
                    if (nameParts.length < 2 || nameParts.length > 3) {
                        pr.hasError = true;
                        pr.errorMsg = "taxon_name value '" + taxNameRaw + "' has " + nameParts.length + " tokens. Expected 'Genus species' or 'Genus species subspecies'.";
                    } else {
                        pr.genus = nameParts[0];
                        pr.species = nameParts[1];
                        if (nameParts.length == 3) {
                            pr.subspecies = nameParts[2];
                        }
                    }
                }
            }
            
            results.add(pr);
        }
        
        return results;
    }
    
    private String safeGet(String[] t, int idx) {
        if (idx == -1 || idx >= t.length) return null;
        String val = t[idx].trim();
        return val.isEmpty() ? null : val;
    }

    private static class ParsedRow {
        int rowNum;
        String rawLine;
        String subfamily;
        String genus;
        String species;
        String subspecies;
        boolean hasError = false;
        String errorMsg;
    }
}

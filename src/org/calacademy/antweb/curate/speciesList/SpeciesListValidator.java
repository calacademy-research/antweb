package org.calacademy.antweb.curate.speciesList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
                        row.rowNum, row.rawLine, "", ValidateSpeciesResultItem.Status.FORMAT_ERROR,
                        "", false, row.errorMsg, "", false));
                continue;
            }

            // Normalization
            String subfamily = normalize(row.subfamily);
            String genus = normalizeCapitalized(row.genus);
            String species = normalize(row.species);
            String subspecies = normalize(row.subspecies);

            if (genus == null || species == null) {
                report.addResult(new ValidateSpeciesResultItem(
                    row.rowNum, row.rawLine, "", ValidateSpeciesResultItem.Status.FORMAT_ERROR,
                    "", false, "Genus and species are required.", "", false));
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

            // Lean DB lookup - only validation fields, avoiding expensive image/bioregion queries.
            TaxonLookupResult result = taxonName != null ? lookupTaxon(taxonName) : null;
            
            if (result != null) {
                if ("valid".equals(result.status)) {
                    report.addResult(new ValidateSpeciesResultItem(
                            row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.EXACT_MATCH,
                            result.status, false, "Exact match.", "", result.fossil));
                    matchedTaxonNames.add(taxonName);
                } else if ("synonym".equals(result.status)) {
                    // current_valid_name is an internal DB key (all lowercase, subfamily-prefixed).
                    // Resolve it to a human-readable display name.
                    String suggestion = resolveDisplayName(result.currentValidName);
                    report.addResult(new ValidateSpeciesResultItem(
                            row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.AMBIGUOUS, 
                            result.status, suggestion != null, "Matched a synonym.", suggestion != null ? suggestion : "", result.fossil));
                } else {
                    report.addResult(new ValidateSpeciesResultItem(
                            row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.AMBIGUOUS, 
                            result.status, false, "Status is '" + result.status + "'. Expected 'valid'.", "", result.fossil));
                }
            } else {
                // Fuzzy match using display names for accurate distance calculation
                FuzzyMatch suggestion = getBestFuzzyMatch(displayName, genus);
                report.addResult(new ValidateSpeciesResultItem(
                        row.rowNum, row.rawLine, displayName, ValidateSpeciesResultItem.Status.NOT_FOUND,
                        "", suggestion.hasValidTaxon(), "Taxon not found.", suggestion.displayText, suggestion.fossil));
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
        String q = "SELECT status, current_valid_name, fossil FROM taxon WHERE taxon_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(q)) {
            stmt.setString(1, taxonName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new TaxonLookupResult(
                            rs.getString("status"),
                            rs.getString("current_valid_name"),
                            rs.getInt("fossil") == 1);
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
                 + " AND taxarank IN ('species','subspecies')"
                 + " ORDER BY CASE WHEN status = 'valid' THEN 0 ELSE 1 END LIMIT 1";
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
        final boolean fossil;
        TaxonLookupResult(String status, String currentValidName, boolean fossil) {
            this.status = status;
            this.currentValidName = currentValidName;
            this.fossil = fossil;
        }
    }

    private static class FuzzyMatch {
        final String displayText;
        final boolean hasValidTaxon;
        final boolean fossil;
        FuzzyMatch(String displayText, boolean hasValidTaxon, boolean fossil) {
            this.displayText = displayText;
            this.hasValidTaxon = hasValidTaxon;
            this.fossil = fossil;
        }
        boolean hasValidTaxon() {
            return hasValidTaxon;
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
    private FuzzyMatch getBestFuzzyMatch(String displayName, String genus) {
        if (genus == null) return new FuzzyMatch("Check spelling.", false, false);
        
        List<FuzzyMatch> candidates = new ArrayList<>();
        // Query individual columns so we can build proper display names
        String query = "SELECT genus, species, subspecies, fossil FROM taxon WHERE genus = ? AND status = 'valid'";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, genus.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String candidate = buildDisplayName(rs.getString("genus"), rs.getString("species"), rs.getString("subspecies"));
                    if (candidate != null) candidates.add(new FuzzyMatch(candidate, true, rs.getInt("fossil") == 1));
                }
            }
        } catch (SQLException e) {
            s_log.warn("Fuzzy match query failed: " + e.getMessage());
            return new FuzzyMatch("Check spelling. (DB error)", false, false);
        }
        
        if (candidates.isEmpty()) return new FuzzyMatch("No valid species found for genus " + genus + ".", false, false);
        
        int bestDistance = Integer.MAX_VALUE;
        FuzzyMatch bestMatch = null;
        LevenshteinDistance ld = new LevenshteinDistance();
        
        for (FuzzyMatch candidate : candidates) {
            int dist = ld.apply(displayName, candidate.displayText);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestMatch = candidate;
            }
        }
        
        if (bestDistance <= 4 && bestMatch != null) {
            return bestMatch;
        }
        
        return new FuzzyMatch("Check spelling.", false, false);
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
        DecodedInput decodedInput = decodeInput(is, charset);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(decodedInput.bytes, decodedInput.offset, decodedInput.bytes.length - decodedInput.offset),
                decodedInput.charset));
        
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

    private DecodedInput decodeInput(InputStream is, String fallbackCharset) throws IOException {
        byte[] bytes = readAllBytes(is);
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            return new DecodedInput(bytes, 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xfe) {
            return new DecodedInput(bytes, 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xff) == 0xfe
                && (bytes[1] & 0xff) == 0xff) {
            return new DecodedInput(bytes, 2, StandardCharsets.UTF_16BE);
        }
        Charset detected = detectUtf16WithoutBom(bytes);
        if (detected != null) {
            return new DecodedInput(bytes, 0, detected);
        }
        return new DecodedInput(bytes, 0, charsetOrUtf8(fallbackCharset));
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private Charset detectUtf16WithoutBom(byte[] bytes) {
        int limit = Math.min(bytes.length, 200);
        if (limit < 20) return null;

        int evenNulls = 0;
        int oddNulls = 0;
        int pairs = limit / 2;
        for (int i = 0; i + 1 < limit; i += 2) {
            if (bytes[i] == 0) evenNulls++;
            if (bytes[i + 1] == 0) oddNulls++;
        }
        if (oddNulls > pairs / 3 && evenNulls == 0) {
            return StandardCharsets.UTF_16LE;
        }
        if (evenNulls > pairs / 3 && oddNulls == 0) {
            return StandardCharsets.UTF_16BE;
        }
        return null;
    }

    private Charset charsetOrUtf8(String charset) {
        if (charset == null || charset.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charset);
        } catch (Exception e) {
            s_log.warn("Unsupported charset '" + charset + "'. Falling back to UTF-8.");
            return StandardCharsets.UTF_8;
        }
    }

    private static class DecodedInput {
        final byte[] bytes;
        final int offset;
        final Charset charset;
        DecodedInput(byte[] bytes, int offset, Charset charset) {
            this.bytes = bytes;
            this.offset = offset;
            this.charset = charset;
        }
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

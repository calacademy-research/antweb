package org.calacademy.antweb.curate.specimen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.calacademy.antweb.Specimen;

public class SpecimenListLookupService {
    public static final int MAX_ROWS = 100000;
    public static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final int maxRows;

    public SpecimenListLookupService() {
        this(MAX_ROWS);
    }

    public SpecimenListLookupService(int maxRows) {
        this.maxRows = maxRows;
    }

    public SpecimenListLookupResult lookup(InputStream input, SpecimenListLookupSource source,
            Writer output) throws IOException, SQLException {
        SpecimenListLookupResult result = new SpecimenListLookupResult();
        List<CodeLine> codeLines = readCodes(input, result);
        result.setRowCount(codeLines.size());
        if (!result.isSuccess()) return result;

        LinkedHashMap<String, String> uniqueCodes = new LinkedHashMap<>();
        for (CodeLine codeLine : codeLines) {
            uniqueCodes.putIfAbsent(normalize(codeLine.code), codeLine.code);
        }

        Map<String, Specimen> specimens = source.findByCodes(new ArrayList<>(uniqueCodes.values()));
        for (CodeLine codeLine : codeLines) {
            if (!specimens.containsKey(normalize(codeLine.code))) {
                result.addError("Line " + codeLine.lineNumber + ": specimen code was not found.");
            }
        }
        if (!result.isSuccess()) return result;

        output.write(Specimen.getTabDelimHeader());
        output.write('\n');
        for (CodeLine codeLine : codeLines) {
            Specimen specimen = specimens.get(normalize(codeLine.code));
            specimen.setCode(codeLine.code);
            output.write(specimen.getTabDelimString());
            output.write('\n');
        }
        output.flush();
        return result;
    }

    public static String validateFilename(String filename) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            return "File must be a plain .txt file.";
        }
        if (!filename.toLowerCase(Locale.ROOT).contains("specimen")) {
            return "Filename must contain 'specimen'.";
        }
        return null;
    }

    private List<CodeLine> readCodes(InputStream input, SpecimenListLookupResult result)
            throws IOException {
        List<CodeLine> codes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\ufeff")) line = line.substring(1);
                String code = line.trim();
                if (code.isEmpty()) continue;
                if (codes.size() == maxRows) {
                    result.addError("File contains more than " + maxRows + " specimen codes.");
                    return codes;
                }
                if (containsWhitespace(code)) {
                    result.addError("Line " + lineNumber
                            + ": specimen code contains whitespace, which is not permitted.");
                    continue;
                }
                codes.add(new CodeLine(lineNumber, code));
            }
        }
        if (codes.isEmpty() && result.isSuccess()) {
            result.addError("File must contain at least one specimen code.");
        }
        return codes;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }

    private static String normalize(String code) {
        return code.toLowerCase(Locale.ROOT);
    }

    private static class CodeLine {
        private final int lineNumber;
        private final String code;

        private CodeLine(int lineNumber, String code) {
            this.lineNumber = lineNumber;
            this.code = code;
        }
    }
}

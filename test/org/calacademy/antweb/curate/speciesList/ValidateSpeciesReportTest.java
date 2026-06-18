package test.org.calacademy.antweb.curate.speciesList;

import junit.framework.TestCase;
import org.calacademy.antweb.curate.speciesList.ValidateSpeciesReport;
import org.calacademy.antweb.curate.speciesList.ValidateSpeciesResultItem;

public class ValidateSpeciesReportTest extends TestCase {

    public void testCategoryMappingUsesStructuredFields() {
        assertCategory("Exact match", exact("valid"));
        assertCategory("Spelling", problem("unavailable misspelling", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        assertCategory("Spelling", notFound(true));
        assertCategory("Not found", notFound(false));
        assertCategory("Junior synonym", problem("synonym", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        assertCategory("Combination changed", problem("obsolete combination", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        assertCategory("Known but not valid", problem("unidentifiable", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        assertCategory("Known but not valid", problem("unavailable", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        assertCategory("Known but not valid", problem("unrecognized", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        assertCategory("Known but not valid", problem("homonym", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        assertCategory("Format error", formatError());
    }

    public void testHeadlineCountsSeparateActionableInformationalAndFormatErrors() {
        ValidateSpeciesReport report = new ValidateSpeciesReport();

        for (int i = 0; i < 23; i++) report.addResult(problem("unavailable misspelling", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        for (int i = 0; i < 24; i++) report.addResult(problem("synonym", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        for (int i = 0; i < 24; i++) report.addResult(problem("obsolete combination", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        for (int i = 0; i < 31; i++) report.addResult(notFound(false));
        for (int i = 0; i < 183; i++) report.addResult(problem("unrecognized", ValidateSpeciesResultItem.Status.AMBIGUOUS, false));
        report.addResult(formatError());
        report.addResult(exact("valid"));

        assertEquals(102, report.getNeedAttentionCount());
        assertEquals(183, report.getInformationalCount());
        assertEquals(1, report.getFormatErrorCount());
        assertEquals(1, report.getExactMatchCount());
    }

    public void testTsvIncludesCategoryAndFossilColumns() {
        ValidateSpeciesReport report = new ValidateSpeciesReport();
        report.addResult(new ValidateSpeciesResultItem(
                2, "Acromyrmex\tbalzani", "Acromyrmex balzani",
                ValidateSpeciesResultItem.Status.AMBIGUOUS,
                "synonym", false, "Matched a synonym.", "Acromyrmex validus", true));

        String tsv = report.generateTsvReport();

        assertTrue(tsv.startsWith("Row\tInput Raw\tNormalized Taxon Name\tStatus\tCategory\tFossil\tMessage\tSuggestion\n"));
        assertTrue(tsv.contains("2\tAcromyrmex  balzani\tAcromyrmex balzani\tAMBIGUOUS\tJunior synonym\tyes\tMatched a synonym.\tAcromyrmex validus\n"));
    }

    private static void assertCategory(String expected, ValidateSpeciesResultItem item) {
        assertEquals(expected, item.getCategory());
    }

    private static ValidateSpeciesResultItem exact(String lookupStatus) {
        return new ValidateSpeciesResultItem(
                1, "input", "Input validus",
                ValidateSpeciesResultItem.Status.EXACT_MATCH,
                lookupStatus, false, "Exact match.", "", false);
    }

    private static ValidateSpeciesResultItem problem(String lookupStatus, ValidateSpeciesResultItem.Status status, boolean fossil) {
        return new ValidateSpeciesResultItem(
                1, "input", "Input invalidus",
                status, lookupStatus, false, "Problem.", "", fossil);
    }

    private static ValidateSpeciesResultItem notFound(boolean hasSuggestedValidName) {
        return new ValidateSpeciesResultItem(
                1, "input", "Input missingus",
                ValidateSpeciesResultItem.Status.NOT_FOUND,
                "", hasSuggestedValidName, "Taxon not found.", hasSuggestedValidName ? "Input validus" : "Check spelling.", false);
    }

    private static ValidateSpeciesResultItem formatError() {
        return new ValidateSpeciesResultItem(
                1, "input", "",
                ValidateSpeciesResultItem.Status.FORMAT_ERROR,
                "", false, "Missing genus or species token.", "", false);
    }
}

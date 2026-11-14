package test.org.calacademy.antweb.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.DataExtractAction;
import org.calacademy.antweb.util.DataExtract;
import org.calacademy.antweb.util.DataExtractFiles;
import org.calacademy.antweb.util.DataExtractJob;
import org.calacademy.antweb.util.JobSummary;

/**
 * The data extract (dataExtract.do; 2026-11-10) without a database.
 *
 * The files read as the command-line tool's (tools/export/specimen_extract.py): the expected CSV lines, manifest
 * text and manifest JSON below were written by that tool's own functions and Python's csv and json modules for the
 * same values (commas, quotes, line breaks, accents, a backslash, thousands separators, the Differences lines).
 *
 * The files on the server (DataExtractFiles): a download is found only by job number and a name from the fixed
 * list (a path, another folder's name, request.json, a link or an unfinished extract give nothing); the zip holds
 * the listed files; extracts older than 30 days are removed and nothing else is; the list reads who asked and
 * the result.
 *
 * The pages: the job's result names VERIFIED or NOT VERIFIED, escapes the note and the manifest, links only the
 * download action, and is not taken for a whole page by the job page; the form keeps and escapes what was typed.
 */
public class DataExtractTest extends TestCase {

    private File root;

    @Override
    protected void setUp() throws Exception {
        root = Files.createTempDirectory("extract-test").toFile();
    }

    @Override
    protected void tearDown() throws Exception {
        delete(root);
    }

    private static void delete(File f) {
        if (!Files.isSymbolicLink(f.toPath()) && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) for (File x : files) delete(x);
        }
        f.delete();
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return m;
    }

    /** The manifest the Python reference texts were made from. */
    private static Map<String, Object> manifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool_version", "10");
        m.put("made_utc", "2026-11-10 14:49:21");
        m.put("antweb_commit", "abc1234");
        m.put("antweb_version", "");
        m.put("countries", Arrays.asList("United States", "Côte d'Ivoire"));
        m.put("islands", new ArrayList<String>());
        m.put("islands_left_out", Arrays.asList("Hawaii"));
        // as the tool has them: in the order the database gave the old names; the text sorts them by island
        m.put("old_island_names", map("Hawaii", Arrays.asList("Sandwich Islands"), "Galapagos Islands", Arrays.asList("Galapagos", "Galápagos")));
        m.put("outdated_names", Arrays.asList(map("name", "U.S.A.", "valid_name", "United States"), map("name", "USA", "valid_name", "United States")));
        m.put("bioregion", "Nearctic");
        m.put("columns", Arrays.asList("code", "has_coordinates"));
        m.put("specimens", 1234567L);
        m.put("verified", false);
        m.put("differences", Arrays.asList(
            map("country", "Côte d'Ivoire", "island_country", "", "in_file", 2L, "in_database", 3L),
            map("country", "United States", "island_country", "Ha\"waii\\", "in_file", 0L, "in_database", 1L),
            map("file", "specimens.csv.gz", "not_formicidae_in_an_ants_only_file", 3L),
            map("file", "specimens_no_country.csv.gz", "in_file", 1623L, "in_database", 1624L)));
        m.put("by_place", Arrays.asList(
            map("country", "Côte d'Ivoire", "island_country", "", "specimens", 2L),
            map("country", "Etats Unis", "island_country", "", "specimens", 1L),
            map("country", "USA", "island_country", "", "specimens", 7L),
            map("country", "United States", "island_country", "Hawaii", "specimens", 1234565L)));
        m.put("by_bioregion", map("", 5L, "Nearctic", 1234562L));
        m.put("by_taxon_status", map("valid", 1234567L));
        m.put("without_coordinates", 1000L);
        m.put("no_taxon_row", 0L);
        m.put("flagged", 12L);
        m.put("ants_only", true);
        m.put("left_out_not_formicidae", map("specimens", 1289L, "families", 7L, "by_family", map("(coleoptera)", 564L, "curculionidae", 353L,
            "vespidae", 224L, "staphylinidae", 100L, "apidae", 30L, "salticidae", 17L, "carabidae", 1L)));
        m.put("blank_family", 4L);
        m.put("not_ants", 3L);
        m.put("filed_under_outdated_name", 7L);
        m.put("countries_without_specimens", Arrays.asList("Côte d'Ivoire"));
        m.put("genera", 80L);
        m.put("names", 2345L);
        m.put("bioregion_list", map("names", 1200L, "names_without_specimen_in_extract", 34L, "names_left_out_not_formicidae", 130L));
        m.put("no_country", map("specimens", 1623L, "in_database", 1624L, "with_coordinates", 400L, "names", 310L,
            "names_not_in_the_countries_file", 45L, "by_group", map("California Academy of Sciences", 710L, " UTEP", 167L, "", 3L)));
        m.put("files", map("specimens.csv.gz", map("sha256", "ab".repeat(32), "bytes", 99L),
            "genus_summary.csv", map("sha256", "cd".repeat(32), "bytes", 7L)));
        return m;
    }

    // Written by tools/export/specimen_extract.py manifest_text() for manifest().
    private static final String PYTHON_TEXT =
        "AntWeb specimen extract\n"
        + "\n"
        + "Made: 2026-11-10 14:49:21 (UTC)   AntWeb commit: abc1234   tool version 10\n"
        + "Countries: United States, Côte d'Ivoire\n"
        + "Islands included (named in the countries): none\n"
        + "Islands of these countries that are their own country in AntWeb, NOT in this file: Hawaii (name an island with the countries to include it)\n"
        + "Old island names read as the island (a specimen marked with one is the island's): Galapagos, Galápagos = Galapagos Islands; Sandwich Islands = Hawaii\n"
        + "Outdated country names included: U.S.A. (now United States), USA (now United States)\n"
        + "\n"
        + "Specimens in the file: 1,234,567\n"
        + "Check against the database: DIFFERENT, see differences; run again\n"
        + "WARNING: AntWeb has no ant specimen for: Côte d'Ivoire (check that these are the countries meant)\n"
        + "\n"
        + "By country (country / island country):\n"
        + "  Côte d'Ivoire                                     2\n"
        + "  Etats Unis                                        1\n"
        + "  USA                                               7\n"
        + "  United States / Hawaii                    1,234,565\n"
        + "\n"
        + "By AntWeb bioregion:\n"
        + "  (none)                                            5\n"
        + "  Nearctic                                  1,234,562\n"
        + "\n"
        + "Country spellings in the file besides the names asked for (MySQL matches names without accents): Etats Unis\n"
        + "\n"
        + "Without usable coordinates: 1,000 (in the file, has_coordinates = 0; a cut by a shape file loses them)\n"
        + "Name has no taxon row: 0 (in the file, no_taxon_row = 1; the API's specimen view leaves these out)\n"
        + "Red-flagged at upload: 12 (column flag)\n"
        + "Ants only (family Formicidae): 1,289 specimens of other families are left out ((coleoptera) 564, curculionidae 353, vespidae 224, staphylinidae 100, apidae 30, salticidae 17, ...); --all-families includes them\n"
        + "No family given: 4 (in the file)\n"
        + "Filed under an outdated country name: 7\n"
        + "Genera: 80   distinct names: 2,345\n"
        + "\n"
        + "AntWeb's Nearctic species list: 1,200 names of ants (130 names of other families left out); 34 of them have no specimen in this extract (literature, or other countries)\n"
        + "\n"
        + "Specimens with no country, in specimens_no_country.csv.gz (all of AntWeb; no country filter finds them; one filed under an included island is in specimens.csv.gz and not here, so no specimen is in both files): 1,623; with coordinates 400; 310 names, 45 of them not in the countries file\n"
        + "  by upload group: California Academy of Sciences 710, UTEP 167, (none) 3\n"
        + "\n"
        + "Files (sha256):\n"
        + "  abababababababababababababababababababababababababababababababab  specimens.csv.gz\n"
        + "  cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd  genus_summary.csv\n"
        + "\n"
        + "Differences:\n"
        + "  {\"country\": \"C\\u00f4te d'Ivoire\", \"island_country\": \"\", \"in_file\": 2, \"in_database\": 3}\n"
        + "  {\"country\": \"United States\", \"island_country\": \"Ha\\\"waii\\\\\", \"in_file\": 0, \"in_database\": 1}\n"
        + "  {\"file\": \"specimens.csv.gz\", \"not_formicidae_in_an_ants_only_file\": 3}\n"
        + "  {\"file\": \"specimens_no_country.csv.gz\", \"in_file\": 1623, \"in_database\": 1624}\n"
        + "\n"
        + "Spreadsheets: import the columns of specimens.csv as text. Values are written as they are in the database,\n"
        + "also when they start with =, +, - or @.\n"
        + "\n"
        + "Cite: AntWeb. Version [version]. California Academy of Sciences, online at https://www.antweb.org. Extract of 2026-11-10.\n"
        + "Images and data on AntWeb are licensed CC BY 4.0.\n";

    // The same manifest verified, with islands, a bioregion list and a version.
    private static final String PYTHON_TEXT_VERIFIED =
        "AntWeb specimen extract\n"
        + "\n"
        + "Made: 2026-11-10 14:49:21 (UTC)   AntWeb commit: abc1234   tool version 10\n"
        + "Countries: United States, Côte d'Ivoire\n"
        + "Islands included (named in the countries): Hawaii, Guam\n"
        + "Islands of these countries that are their own country in AntWeb, NOT in this file: none\n"
        + "Old island names read as the island (a specimen marked with one is the island's): none\n"
        + "Outdated country names included: none\n"
        + "\n"
        + "Specimens in the file: 1,234,567\n"
        + "Check against the database: VERIFIED, every count equal\n"
        + "\n"
        + "By country (country / island country):\n"
        + "  Côte d'Ivoire                                     2\n"
        + "  United States / Hawaii                    1,234,565\n"
        + "\n"
        + "By AntWeb bioregion:\n"
        + "  (none)                                            5\n"
        + "  Nearctic                                  1,234,562\n"
        + "\n"
        + "Without usable coordinates: 1,000 (in the file, has_coordinates = 0; a cut by a shape file loses them)\n"
        + "Name has no taxon row: 0 (in the file, no_taxon_row = 1; the API's specimen view leaves these out)\n"
        + "Red-flagged at upload: 12 (column flag)\n"
        + "All families (--all-families): 3 specimens in the file are not Formicidae\n"
        + "No family given: 4 (in the file)\n"
        + "Filed under an outdated country name: 0\n"
        + "Genera: 80   distinct names: 2,345\n"
        + "\n"
        + "AntWeb's Nearctic species list: 1,330 names; 34 of them have no specimen in this extract (literature, or other countries)\n"
        + "\n"
        + "Files (sha256):\n"
        + "  abababababababababababababababababababababababababababababababab  specimens.csv.gz\n"
        + "  cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd  genus_summary.csv\n"
        + "\n"
        + "Spreadsheets: import the columns of specimens.csv as text. Values are written as they are in the database,\n"
        + "also when they start with =, +, - or @.\n"
        + "\n"
        + "Cite: AntWeb. Version 9.4.0. California Academy of Sciences, online at https://www.antweb.org. Extract of 2026-11-10.\n"
        + "Images and data on AntWeb are licensed CC BY 4.0.\n";

    // Written by Python's json.dump(manifest, indent=1, ensure_ascii=False).
    private static final String PYTHON_JSON =
        "{\n"
        + " \"tool_version\": \"10\",\n"
        + " \"made_utc\": \"2026-11-10 14:49:21\",\n"
        + " \"antweb_commit\": \"abc1234\",\n"
        + " \"antweb_version\": \"\",\n"
        + " \"countries\": [\n"
        + "  \"United States\",\n"
        + "  \"Côte d'Ivoire\"\n"
        + " ],\n"
        + " \"islands\": [],\n"
        + " \"islands_left_out\": [\n"
        + "  \"Hawaii\"\n"
        + " ],\n"
        + " \"old_island_names\": {\n"
        + "  \"Hawaii\": [\n"
        + "   \"Sandwich Islands\"\n"
        + "  ],\n"
        + "  \"Galapagos Islands\": [\n"
        + "   \"Galapagos\",\n"
        + "   \"Galápagos\"\n"
        + "  ]\n"
        + " },\n"
        + " \"outdated_names\": [\n"
        + "  {\n"
        + "   \"name\": \"U.S.A.\",\n"
        + "   \"valid_name\": \"United States\"\n"
        + "  },\n"
        + "  {\n"
        + "   \"name\": \"USA\",\n"
        + "   \"valid_name\": \"United States\"\n"
        + "  }\n"
        + " ],\n"
        + " \"bioregion\": \"Nearctic\",\n"
        + " \"columns\": [\n"
        + "  \"code\",\n"
        + "  \"has_coordinates\"\n"
        + " ],\n"
        + " \"specimens\": 1234567,\n"
        + " \"verified\": false,\n"
        + " \"differences\": [\n"
        + "  {\n"
        + "   \"country\": \"Côte d'Ivoire\",\n"
        + "   \"island_country\": \"\",\n"
        + "   \"in_file\": 2,\n"
        + "   \"in_database\": 3\n"
        + "  },\n"
        + "  {\n"
        + "   \"country\": \"United States\",\n"
        + "   \"island_country\": \"Ha\\\"waii\\\\\",\n"
        + "   \"in_file\": 0,\n"
        + "   \"in_database\": 1\n"
        + "  },\n"
        + "  {\n"
        + "   \"file\": \"specimens.csv.gz\",\n"
        + "   \"not_formicidae_in_an_ants_only_file\": 3\n"
        + "  },\n"
        + "  {\n"
        + "   \"file\": \"specimens_no_country.csv.gz\",\n"
        + "   \"in_file\": 1623,\n"
        + "   \"in_database\": 1624\n"
        + "  }\n"
        + " ],\n"
        + " \"by_place\": [\n"
        + "  {\n"
        + "   \"country\": \"Côte d'Ivoire\",\n"
        + "   \"island_country\": \"\",\n"
        + "   \"specimens\": 2\n"
        + "  },\n"
        + "  {\n"
        + "   \"country\": \"Etats Unis\",\n"
        + "   \"island_country\": \"\",\n"
        + "   \"specimens\": 1\n"
        + "  },\n"
        + "  {\n"
        + "   \"country\": \"USA\",\n"
        + "   \"island_country\": \"\",\n"
        + "   \"specimens\": 7\n"
        + "  },\n"
        + "  {\n"
        + "   \"country\": \"United States\",\n"
        + "   \"island_country\": \"Hawaii\",\n"
        + "   \"specimens\": 1234565\n"
        + "  }\n"
        + " ],\n"
        + " \"by_bioregion\": {\n"
        + "  \"\": 5,\n"
        + "  \"Nearctic\": 1234562\n"
        + " },\n"
        + " \"by_taxon_status\": {\n"
        + "  \"valid\": 1234567\n"
        + " },\n"
        + " \"without_coordinates\": 1000,\n"
        + " \"no_taxon_row\": 0,\n"
        + " \"flagged\": 12,\n"
        + " \"ants_only\": true,\n"
        + " \"left_out_not_formicidae\": {\n"
        + "  \"specimens\": 1289,\n"
        + "  \"families\": 7,\n"
        + "  \"by_family\": {\n"
        + "   \"(coleoptera)\": 564,\n"
        + "   \"curculionidae\": 353,\n"
        + "   \"vespidae\": 224,\n"
        + "   \"staphylinidae\": 100,\n"
        + "   \"apidae\": 30,\n"
        + "   \"salticidae\": 17,\n"
        + "   \"carabidae\": 1\n"
        + "  }\n"
        + " },\n"
        + " \"blank_family\": 4,\n"
        + " \"not_ants\": 3,\n"
        + " \"filed_under_outdated_name\": 7,\n"
        + " \"countries_without_specimens\": [\n"
        + "  \"Côte d'Ivoire\"\n"
        + " ],\n"
        + " \"genera\": 80,\n"
        + " \"names\": 2345,\n"
        + " \"bioregion_list\": {\n"
        + "  \"names\": 1200,\n"
        + "  \"names_without_specimen_in_extract\": 34,\n"
        + "  \"names_left_out_not_formicidae\": 130\n"
        + " },\n"
        + " \"no_country\": {\n"
        + "  \"specimens\": 1623,\n"
        + "  \"in_database\": 1624,\n"
        + "  \"with_coordinates\": 400,\n"
        + "  \"names\": 310,\n"
        + "  \"names_not_in_the_countries_file\": 45,\n"
        + "  \"by_group\": {\n"
        + "   \"California Academy of Sciences\": 710,\n"
        + "   \" UTEP\": 167,\n"
        + "   \"\": 3\n"
        + "  }\n"
        + " },\n"
        + " \"files\": {\n"
        + "  \"specimens.csv.gz\": {\n"
        + "   \"sha256\": \"abababababababababababababababababababababababababababababababab\",\n"
        + "   \"bytes\": 99\n"
        + "  },\n"
        + "  \"genus_summary.csv\": {\n"
        + "   \"sha256\": \"cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd\",\n"
        + "   \"bytes\": 7\n"
        + "  }\n"
        + " }\n"
        + "}";

    // Written by Python's csv.writer for the three rows of testCsvAsPythonWritesIt.
    private static final String PYTHON_CSV =
        "a,,\"b,c\",\"say \"\"hi\"\"\",\"line1\n"
        + "line2\",\"cr\rx\", lead,tab\there,\u00e9,5,back\\slash,'q'\r\n"
        + "\"\"\r\n"
        + ",\r\n";

    public void testManifestTextAsTheCommandLineToolWritesIt() {
        assertEquals(PYTHON_TEXT, DataExtract.manifestText(manifest()));
        Map<String, Object> m = manifest();
        m.put("verified", true);
        m.put("differences", new ArrayList<Object>());
        m.put("islands", Arrays.asList("Hawaii", "Guam"));
        m.put("islands_left_out", new ArrayList<String>());
        m.put("old_island_names", new LinkedHashMap<String, Object>());
        m.put("outdated_names", new ArrayList<Object>());
        m.put("countries_without_specimens", new ArrayList<String>());
        m.put("filed_under_outdated_name", 0L);
        m.put("ants_only", false);
        m.put("left_out_not_formicidae", null);
        m.put("no_country", null);
        m.put("by_place", Arrays.asList(
            map("country", "Côte d'Ivoire", "island_country", "", "specimens", 2L),
            map("country", "United States", "island_country", "Hawaii", "specimens", 1234565L)));
        m.put("bioregion", "Nearctic");
        m.put("antweb_version", "9.4.0");
        m.put("bioregion_list", map("names", 1330L, "names_without_specimen_in_extract", 34L, "names_left_out_not_formicidae", null));
        assertEquals(PYTHON_TEXT_VERIFIED, DataExtract.manifestText(m));
        // what the reference texts hold: the old names, the warning, the other spelling, the count, the spreadsheet line
        assertTrue(PYTHON_TEXT.contains("Outdated country names included: U.S.A. (now United States), USA (now United States)\n"));
        // format 8: the warning counts what the file can hold (ants, when ants only)
        assertTrue(PYTHON_TEXT.contains("WARNING: AntWeb has no ant specimen for: Côte d'Ivoire (check that these are the countries meant)\n"));
        Map<String, Object> allFamilies = manifest();
        allFamilies.put("ants_only", false);
        allFamilies.put("left_out_not_formicidae", null);
        assertTrue(DataExtract.manifestText(allFamilies).contains("WARNING: AntWeb has no specimen for: Côte d'Ivoire (check"));
        // formats 9 and 10 (Brian Fisher, 2026-11-11: an island that is its own country is never part of its political
        // parent): the islands named in the countries, the islands left out, the old island names (sorted by island)
        assertTrue(PYTHON_TEXT, PYTHON_TEXT.contains("Countries: United States, Côte d'Ivoire\nIslands included (named in the countries): none\n"
            + "Islands of these countries that are their own country in AntWeb, NOT in this file: Hawaii (name an island with the countries to include it)\n"
            + "Old island names read as the island (a specimen marked with one is the island's): Galapagos, Galápagos = Galapagos Islands; Sandwich Islands = Hawaii\n"
            + "Outdated country names included:"));
        assertTrue(PYTHON_TEXT_VERIFIED, PYTHON_TEXT_VERIFIED.contains("Islands included (named in the countries): Hawaii, Guam\n"
            + "Islands of these countries that are their own country in AntWeb, NOT in this file: none\n"
            + "Old island names read as the island (a specimen marked with one is the island's): none\n"));
        assertTrue("the JSON keeps the old island names in the order the tool has them", PYTHON_JSON.contains(" \"islands\": [],\n \"islands_left_out\": [\n  \"Hawaii\"\n ],\n"
            + " \"old_island_names\": {\n  \"Hawaii\": [\n   \"Sandwich Islands\"\n  ],\n  \"Galapagos Islands\": [\n   \"Galapagos\",\n   \"Galápagos\"\n  ]\n },\n \"outdated_names\": ["));
        for (String text : new String[] {PYTHON_TEXT, PYTHON_TEXT_VERIFIED}) {
            assertFalse("no text says a country's islands come with it", text.contains("Islands included: "));
        }
        // a manifest of before format 9 (no such keys) still reads
        Map<String, Object> older = manifest();
        older.remove("islands_left_out");
        older.remove("old_island_names");
        assertTrue(DataExtract.manifestText(older).contains("NOT in this file: none\nOld island names read as the island (a specimen marked with one is the island's): none\n"));
        assertTrue(PYTHON_TEXT.contains("Country spellings in the file besides the names asked for (MySQL matches names without accents): Etats Unis\n"));
        assertTrue(PYTHON_TEXT.contains("Filed under an outdated country name: 7\n"));
        assertTrue(PYTHON_TEXT.contains("Spreadsheets: import the columns of specimens.csv as text."));
        assertTrue(PYTHON_TEXT_VERIFIED.contains("Outdated country names included: none\n") && !PYTHON_TEXT_VERIFIED.contains("WARNING"));
        // format 5 (Brian Fisher, 2026-11-10: ants only by default): what is left out, the six largest families and "..."
        assertTrue(PYTHON_TEXT, PYTHON_TEXT.contains("Ants only (family Formicidae): 1,289 specimens of other families are left out ((coleoptera) 564,"
            + " curculionidae 353, vespidae 224, staphylinidae 100, apidae 30, salticidae 17, ...); --all-families includes them\nNo family given: 4 (in the file)\n"));
        assertTrue(PYTHON_TEXT_VERIFIED, PYTHON_TEXT_VERIFIED.contains("All families (--all-families): 3 specimens in the file are not Formicidae\n"));
        assertTrue(PYTHON_TEXT.contains("{\"file\": \"specimens.csv.gz\", \"not_formicidae_in_an_ants_only_file\": 3}\n"));
        // format 6: the bioregion's species list is of ants only too, and says how many names it leaves out
        assertTrue(PYTHON_TEXT, PYTHON_TEXT.contains("AntWeb's Nearctic species list: 1,200 names of ants (130 names of other families left out); 34 of them have no specimen in this extract (literature, or other countries)\n"));
        assertTrue(PYTHON_TEXT_VERIFIED, PYTHON_TEXT_VERIFIED.contains("AntWeb's Nearctic species list: 1,330 names; 34 of them have no specimen in this extract (literature, or other countries)\n"));
        // the file of the specimens without a country: its line, groups largest first, a group without a name
        assertTrue(PYTHON_TEXT, PYTHON_TEXT.contains("Specimens with no country, in specimens_no_country.csv.gz (all of AntWeb; no country filter finds them; "
            + "one filed under an included island is in specimens.csv.gz and not here, so no specimen is in both files): "
            + "1,623; with coordinates 400; 310 names, 45 of them not in the countries file\n  by upload group: California Academy of Sciences 710, UTEP 167, (none) 3\n"));
        assertFalse(PYTHON_TEXT_VERIFIED.contains("Specimens with no country"));
        // ants only with nothing left out: no list of families
        Map<String, Object> none = manifest();
        none.put("left_out_not_formicidae", map("specimens", 0L, "families", 0L, "by_family", new LinkedHashMap<String, Object>()));
        assertTrue(DataExtract.manifestText(none).contains("Ants only (family Formicidae): 0 specimens of other families are left out; --all-families includes them\n"));
    }

    public void testManifestJsonAsPythonWritesIt() {
        assertEquals(PYTHON_JSON, DataExtract.json(manifest()));
        assertEquals("{}", DataExtract.json(new LinkedHashMap<String, Object>()));
        assertEquals("\"a\\tb\\u0001é\"", DataExtract.json("a\tb\u0001é"));
    }

    public void testCsvAsPythonWritesIt() throws Exception {
        StringWriter w = new StringWriter();
        DataExtract.csvRow(w, Arrays.asList("a", "", "b,c", "say \"hi\"", "line1\nline2", "cr\rx", " lead", "tab\there", "é", 5, "back\\slash", "'q'"));
        DataExtract.csvRow(w, Arrays.asList(""));
        DataExtract.csvRow(w, Arrays.asList("", null));
        assertEquals(PYTHON_CSV, w.toString());
        assertEquals("plain", DataExtract.csvField("plain"));
        assertEquals("", DataExtract.csvField(null));
    }

    public void testCoordinatesNamesAndSqlHelpers() throws Exception {
        assertTrue(DataExtract.hasCoordinates("37.9", "-122.6"));
        assertTrue(DataExtract.hasCoordinates("0", "10"));
        assertTrue(DataExtract.hasCoordinates(" 90 ", "-180"));
        for (String[] no : new String[][] {{"0", "0"}, {"0.0", "-0"}, {"", ""}, {"95", "10"}, {"10", "181"}, {"abc", "1"}, {"1", ""}, {"NaN", "1"}}) {
            assertFalse(no[0] + "/" + no[1], DataExtract.hasCoordinates(no[0], no[1]));
        }
        assertEquals(Arrays.asList("United States", "Canada"), DataExtract.parseCountries(" United States , Canada ,\n United States"));
        assertEquals(Arrays.asList("Mexico", "Cote d'Ivoire"), DataExtract.parseCountries("Mexico\r\nCote d'Ivoire"));
        assertEquals(new ArrayList<String>(), DataExtract.parseCountries(" , "));
        for (String bad : new String[] {"x; drop table specimen", "a\\b", "tab\there", "x".repeat(65)}) {
            try {
                DataExtract.parseCountries(bad);
                fail("accepted " + bad);
            } catch (DataExtract.BadRequest e) {
                assertTrue(e.getMessage(), e.getMessage().startsWith("Not a place name"));
            }
        }
        assertEquals("'Cote d''Ivoire'", DataExtract.sqlString("Cote d'Ivoire"));
        assertEquals("'a\\\\b'", DataExtract.sqlString("a\\b"));
        // The conditions as tools/export/specimen_extract.py (version 10) writes them. Brian Fisher, 2026-11-11: an island
        // that is its own country is never part of its political parent, so a specimen marked as an island's that was
        // not asked for is left out whatever its country column says, and a named island is found by both columns.
        List<String> none = new ArrayList<>();
        String ants = " and (s.family = 'formicidae' or s.family is null or s.family = '')";
        assertEquals("(s.country in ('Canada'))", DataExtract.whereSql(Arrays.asList("Canada"), none, false, none));
        assertEquals("(s.country in ('United States', 'Canada')) and (s.island_country is null or s.island_country not in ('Galapagos Islands', 'Galapagos', 'Hawaii'))" + ants,
            DataExtract.whereSql(Arrays.asList("United States", "Canada"), none, true, Arrays.asList("Galapagos Islands", "Galapagos", "Hawaii")));
        assertEquals("(s.country in ('United States', 'Hawaii') or s.island_country in ('Hawaii', 'Sandwich Islands'))"
            + " and (s.island_country is null or s.island_country not in ('Galapagos Islands'))",
            DataExtract.whereSql(Arrays.asList("United States", "Hawaii"), Arrays.asList("Hawaii", "Sandwich Islands"), false, Arrays.asList("Galapagos Islands")));
        // the specimens without a country: not the ones already in the countries file under an included island
        assertEquals("(s.country is null or s.country = '') and (s.island_country is null or s.island_country not in ('Hawaii'))" + ants,
            DataExtract.noCountryWhere(Arrays.asList("Hawaii"), true));
        assertEquals("(s.country is null or s.country = '')", DataExtract.noCountryWhere(none, false));
        // the count behind the warning: what the file can hold (ants, when ants only); a named island by both columns
        DataExtract.Islands islands = new DataExtract.Islands();
        islands.included.addAll(Arrays.asList("Galapagos Islands", "Hawaii"));
        islands.oldNames.put("Galapagos Islands", Arrays.asList("Galapagos"));
        assertEquals(Arrays.asList("Galapagos Islands", "Galapagos", "Hawaii"), islands.marked(islands.included));
        List<Map<String, Object>> outdated = new ArrayList<>();
        outdated.add(map("name", "USA", "valid_name", "United States"));
        assertEquals("select 0, count(*) from specimen s where s.country in ('United States', 'USA')" + ants
            + " union all select 1, count(*) from specimen s where (s.country in ('Hawaii') or s.island_country = 'Hawaii')" + ants
            + " union all select 2, count(*) from specimen s where (s.country in ('Galapagos Islands') or s.island_country in ('Galapagos Islands', 'Galapagos'))" + ants
            + " /* per country */",
            DataExtract.perCountrySql(Arrays.asList("United States", "Hawaii", "Galapagos Islands"), outdated, true, islands));
        assertEquals("select 0, count(*) from specimen s where s.country in ('Canada') /* per country */",
            DataExtract.perCountrySql(Arrays.asList("Canada"), new ArrayList<Map<String, Object>>(), false, new DataExtract.Islands()));
        // numbers and dates are taken as the database prints them; text through the driver
        for (int t : new int[] {Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.INTEGER, Types.TINYINT, Types.BIT, Types.BIGINT,
            Types.DATE, Types.TIMESTAMP, Types.TIME}) assertTrue("type " + t, DataExtract.isRawText(t));
        for (int t : new int[] {Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.LONGVARBINARY, Types.BLOB}) assertFalse("type " + t, DataExtract.isRawText(t));
    }

    public void testADifferentCountIsNamed() {
        Map<List<String>, Long> written = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        Map<List<String>, Long> database = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        written.put(Arrays.asList("Canada", ""), 1L);
        written.put(Arrays.asList("United States", ""), 2L);
        written.put(Arrays.asList("Hawaii", "Hawaii"), 1L);
        database.putAll(written);
        assertEquals(new ArrayList<Object>(), DataExtract.compareCounts(written, database));
        database.put(Arrays.asList("United States", ""), 3L);        // one more in the database
        database.put(Arrays.asList("Mexico", ""), 4L);               // a place the file has nothing of
        database.remove(Arrays.asList("Hawaii", "Hawaii"));          // a place the database no longer has
        assertEquals("[{country=Hawaii, island_country=Hawaii, in_file=1, in_database=0}, {country=Mexico, island_country=, in_file=0, in_database=4},"
            + " {country=United States, island_country=, in_file=2, in_database=3}]", DataExtract.compareCounts(written, database).toString());
    }

    // ------------------------------------------------------------------ the files on the server

    private File finished(LocalDate day, int jobId, boolean verified) throws Exception {
        File folder = DataExtractFiles.create(root, day, jobId);
        DataExtractFiles.writeRequest(folder, jobId, 7, "bfisher", "for the \"Nearctic\" <team>",
            new DataExtract.Request(Arrays.asList("United States", "Canada"), "Nearctic"));
        for (String name : DataExtractFiles.DOWNLOADS) {
            Files.write(new File(folder, name).toPath(), (name.equals(DataExtract.MANIFEST_JSON)
                ? "{\"specimens\": 1234, \"verified\": " + verified + ", \"countries\": [\"United States\", \"Canada\"], \"islands_left_out\": [\"Hawaii\"]}" : "content of " + name)
                .getBytes(StandardCharsets.UTF_8));
        }
        return folder;
    }

    public void testADownloadIsFoundOnlyByJobNumberAndAListedName() throws Exception {
        File folder = finished(LocalDate.of(2026, 11, 10), 123, true);
        assertEquals("2026-11-10-123", folder.getName());
        assertEquals(folder, DataExtractFiles.find(root, 123));
        for (String name : DataExtractFiles.DOWNLOADS) {
            assertEquals(new File(folder, name), DataExtractFiles.file(root, 123, name));
        }
        assertEquals(6, DataExtractFiles.files(root, 123).size());
        // not on the list: the private request, paths, other spellings
        for (String name : new String[] {"request.json", "../2026-11-10-123/manifest.txt", "/etc/passwd", "..", "", "MANIFEST.TXT",
            "manifest.txt/", "2026-11-10-123/manifest.txt", "manifest.txt\u0000", "all.zip", null}) {
            assertNull(String.valueOf(name), DataExtractFiles.file(root, 123, name));
        }
        // another job, no job
        assertNull(DataExtractFiles.file(root, 12, DataExtract.MANIFEST_TXT));
        assertNull(DataExtractFiles.file(root, 1234, DataExtract.MANIFEST_TXT));
        assertNull(DataExtractFiles.file(root, 0, DataExtract.MANIFEST_TXT));
        assertNull(DataExtractFiles.file(root, -123, DataExtract.MANIFEST_TXT));
        assertNull(DataExtractFiles.find(new File(root, "missing"), 123));
        // a folder that is not an extract's is never found, whatever its number
        new File(root, "123").mkdir();
        new File(root, "notes-124").mkdir();
        assertNull(DataExtractFiles.find(root, 124));
        assertEquals("antweb-extract-2026-11-10-123-specimens.csv.gz", DataExtractFiles.downloadName(folder, DataExtract.SPECIMENS, true));
        assertEquals("antweb-extract-2026-11-10-123.zip", DataExtractFiles.downloadName(folder, DataExtractFiles.ZIP, true));
        assertTrue(DataExtractFiles.isVerified(root, 123));
        assertFalse(DataExtractFiles.isVerified(root, 124));
        assertEquals("application/gzip", DataExtractFiles.contentType("antweb-extract-2026-11-10-123-specimens.csv.gz"));
        assertEquals("text/plain; charset=UTF-8", DataExtractFiles.contentType("x-manifest.txt"));
    }

    public void testAnUnfinishedExtractAndLinksGiveNothing() throws Exception {
        // no manifest yet: the specimens file is being written
        File running = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 200);
        Files.write(new File(running, DataExtract.SPECIMENS).toPath(), new byte[] {1, 2, 3});
        assertNull(DataExtractFiles.file(root, 200, DataExtract.SPECIMENS));
        assertEquals(0, DataExtractFiles.files(root, 200).size());
        // a link in an extract's folder is not a file of the extract
        File secret = new File(root, "secret.txt");
        Files.write(secret.toPath(), "secret".getBytes(StandardCharsets.UTF_8));
        File folder = finished(LocalDate.of(2026, 11, 10), 201, true);
        File linked = new File(folder, DataExtract.GENUS_SUMMARY);
        linked.delete();
        Files.createSymbolicLink(linked.toPath(), secret.toPath());
        assertNull(DataExtractFiles.file(root, 201, DataExtract.GENUS_SUMMARY));
        assertEquals(5, DataExtractFiles.files(root, 201).size());
        // a link named like an extract folder is not an extract
        File elsewhere = Files.createTempDirectory("extract-elsewhere").toFile();
        try {
            Files.write(new File(elsewhere, DataExtract.MANIFEST_JSON).toPath(), "{}".getBytes(StandardCharsets.UTF_8));
            Files.createSymbolicLink(new File(root, "2026-11-10-202").toPath(), elsewhere.toPath());
            assertNull(DataExtractFiles.find(root, 202));
            assertNull(DataExtractFiles.file(root, 202, DataExtract.MANIFEST_JSON));
            assertEquals(0, DataExtractFiles.removeOlderThan(root, 0, LocalDate.of(2027, 1, 1)) - 2);   // 200 and 201 removed, the link left
            assertTrue(new File(elsewhere, DataExtract.MANIFEST_JSON).exists());
            assertTrue(secret.exists());
        } finally {
            delete(elsewhere);
        }
    }

    public void testTheZipHoldsTheListedFiles() throws Exception {
        finished(LocalDate.of(2026, 11, 10), 300, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataExtractFiles.writeZip(DataExtractFiles.files(root, 300), out);
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                names.add(e.getName());
                assertEquals(e.getName().equals(DataExtract.MANIFEST_JSON) ? "{\"specimens\": 1234, \"verified\": true, \"countries\": [\"United States\", \"Canada\"], \"islands_left_out\": [\"Hawaii\"]}"
                    : "content of " + e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(DataExtractFiles.DOWNLOADS, names);   // never request.json
    }

    public void testOldExtractsAreRemovedAndNothingElse() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 10);
        File old = finished(today.minusDays(31), 1, true);
        File edge = finished(today.minusDays(30), 2, true);
        File recent = finished(today, 3, false);
        File other = new File(root, "keep-me");
        other.mkdir();
        Files.write(new File(root, "2020-01-01-9").toPath(), new byte[0]);       // a file named like a folder
        File odd = new File(root, "2020-13-45-8");                                // not a date
        odd.mkdir();
        assertEquals(1, DataExtractFiles.removeOlderThan(root, DataExtractFiles.KEEP_DAYS, today));
        assertFalse(old.exists());
        assertTrue(edge.exists() && recent.exists() && other.exists() && odd.exists() && new File(root, "2020-01-01-9").exists());
        assertEquals(0, DataExtractFiles.removeOlderThan(root, DataExtractFiles.KEEP_DAYS, today));
        assertEquals(0, DataExtractFiles.removeOlderThan(new File(root, "missing"), DataExtractFiles.KEEP_DAYS, today));
        // an extract that failed keeps its request, loses its data files
        DataExtractFiles.deleteData(recent);
        assertEquals("[request.json]", Arrays.toString(recent.list()));
        assertEquals(30, DataExtractFiles.KEEP_DAYS);
    }

    public void testTheListReadsWhoAskedAndTheResult() throws Exception {
        finished(LocalDate.of(2026, 11, 9), 41, true);
        finished(LocalDate.of(2026, 11, 10), 42, false);
        File running = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 43);
        DataExtractFiles.writeRequest(running, 43, 7, "bfisher", "", new DataExtract.Request(Arrays.asList("Mexico"), "", true, true));
        File broken = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 44);
        Files.write(new File(broken, DataExtractFiles.REQUEST).toPath(), "not json".getBytes(StandardCharsets.UTF_8));
        List<DataExtractFiles.Entry> list = DataExtractFiles.list(root);
        assertEquals(4, list.size());
        assertEquals(44, list.get(0).jobId);                      // newest first; an unreadable request does not stop the list
        assertEquals("", list.get(0).loginName);
        DataExtractFiles.Entry e43 = list.get(1), e42 = list.get(2), e41 = list.get(3);
        assertEquals("bfisher Mexico []", e43.loginName + " " + String.join(",", e43.countries) + " " + e43.islandsLeftOut);
        assertFalse(e43.isFinished());
        assertTrue("all families and the no-country file, as asked", e43.allFamilies && e43.noCountry);
        assertTrue(e43.files.isEmpty());
        assertNull(e43.specimens);
        assertEquals(Boolean.FALSE, e42.verified);
        assertEquals(Boolean.TRUE, e41.verified);
        assertEquals(Long.valueOf(1234), e41.specimens);
        assertEquals("for the \"Nearctic\" <team>", e41.note);
        assertEquals(Arrays.asList("United States", "Canada"), e41.countries);
        assertEquals("Nearctic", e41.bioregion);
        assertEquals("the islands left out, from the manifest", Arrays.asList("Hawaii"), e41.islandsLeftOut);
        String request = new String(Files.readAllBytes(new File(e41.folder, DataExtractFiles.REQUEST).toPath()), StandardCharsets.UTF_8);
        assertFalse("no islands switch in the request any more (Brian Fisher, 2026-11-11)", request.contains("islands"));
        assertEquals(7, e41.loginId);
        assertFalse("ants only, no file of the specimens without a country, unless asked", e41.allFamilies || e41.noCountry);
        assertEquals(LocalDate.of(2026, 11, 9), e41.day);
        assertEquals(DataExtractFiles.DOWNLOADS, e41.files);
        assertTrue(e41.bytes > 0);
        assertEquals(0, DataExtractFiles.list(new File(root, "missing")).size());
    }

    /** Review M1: the folder was inside Tomcat's web root, kept private by one Caddy rule. */
    public void testTheFolderIsOutsideTheWebApplication() throws Exception {
        String folder = DataExtractFiles.root().getCanonicalPath() + "/";
        String webRoot = new File(org.calacademy.antweb.util.AntwebProps.getDocRoot()).getCanonicalPath() + "/";
        String dataRoot = new File(org.calacademy.antweb.util.AntwebProps.getDataRoot()).getCanonicalPath() + "/";
        assertEquals("/usr/local/tomcat/logs/extracts", DataExtractFiles.root().getPath());
        assertFalse(folder + " is under the web root " + webRoot, folder.startsWith(webRoot));
        assertFalse(folder + " is under the data root " + dataRoot, folder.startsWith(dataRoot));
        // logs/web is the web application's web/log (docker-compose.yml): the extracts are not in it
        assertFalse(folder.startsWith("/usr/local/tomcat/logs/web/"));
    }

    /** Review L4: after staging's database is refreshed, job numbers start again. */
    public void testAJobNumberInTwoFoldersIsTheNewestAndANewExtractReplacesIt() throws Exception {
        File old = finished(LocalDate.of(2026, 10, 1), 77, true);
        File newer = finished(LocalDate.of(2026, 11, 9), 77, false);
        assertEquals(newer, DataExtractFiles.find(root, 77));
        assertEquals(new File(newer, DataExtract.MANIFEST_TXT), DataExtractFiles.file(root, 77, DataExtract.MANIFEST_TXT));
        assertFalse("the newest one is the extract of that number: not verified", DataExtractFiles.isVerified(root, 77));
        // a new extract with the number: the old folders go, so a download never finds old files under the new number
        File fresh = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 77);
        assertFalse(old.exists() || newer.exists());
        assertEquals(fresh, DataExtractFiles.find(root, 77));
        assertNull(DataExtractFiles.file(root, 77, DataExtract.MANIFEST_TXT));
        assertEquals("[]", Arrays.toString(fresh.list()));
        // the same day again (a job that is run twice cannot happen, but the folder is then emptied too)
        Files.write(new File(fresh, DataExtract.SPECIMENS).toPath(), new byte[] {1});
        assertEquals("[]", Arrays.toString(DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 77).list()));
        try {
            DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 0);
            fail("a folder without a job number");
        } catch (java.io.IOException e) {
            // expected
        }
    }

    /** Review L3: the downloads opened as soon as a file named manifest.json existed, also a half-written one. */
    public void testAHalfWrittenManifestOpensNoDownload() throws Exception {
        File folder = finished(LocalDate.of(2026, 11, 10), 88, true);
        File manifest = new File(folder, DataExtract.MANIFEST_JSON);
        for (String half : new String[] {"", "{", "{\"specimens\": 12", "{\"specimens\": 12}", "{\"verified\": true}", "[1, 2]", "null",
            "{\"specimens\": \"many\", \"verified\": true}"}) {
            Files.write(manifest.toPath(), half.getBytes(StandardCharsets.UTF_8));
            assertNull("manifest: " + half, DataExtractFiles.file(root, 88, DataExtract.SPECIMENS));
            assertEquals(0, DataExtractFiles.files(root, 88).size());
            assertFalse(DataExtractFiles.isVerified(root, 88));
        }
        // while it is written it has another name, which opens nothing either
        manifest.delete();
        Files.write(new File(folder, DataExtract.MANIFEST_JSON + DataExtract.PART).toPath(), "{\"specimens\": 12, \"verified\": true}".getBytes(StandardCharsets.UTF_8));
        assertNull(DataExtractFiles.file(root, 88, DataExtract.SPECIMENS));
        assertNull(DataExtractFiles.file(root, 88, DataExtract.MANIFEST_JSON + DataExtract.PART));
        // whole again
        Files.write(manifest.toPath(), "{\"specimens\": 12, \"verified\": true}".getBytes(StandardCharsets.UTF_8));
        assertNotNull(DataExtractFiles.file(root, 88, DataExtract.SPECIMENS));
    }

    /** Review L2: a NOT VERIFIED extract could be downloaded as a zip labelled "the file to send". */
    public void testANotVerifiedExtractHasNoZipAndSaysSoInItsName() throws Exception {
        File folder = finished(LocalDate.of(2026, 11, 10), 99, false);
        assertFalse(DataExtractFiles.isVerified(root, 99));
        assertNotNull("its files can still be looked at", DataExtractFiles.file(root, 99, DataExtract.MANIFEST_TXT));
        assertEquals("antweb-extract-NOT-VERIFIED-2026-11-10-99-specimens.csv.gz", DataExtractFiles.downloadName(folder, DataExtract.SPECIMENS, false));
        assertEquals("antweb-extract-NOT-VERIFIED-2026-11-10-99-manifest.txt", DataExtractFiles.downloadName(folder, DataExtract.MANIFEST_TXT, false));
        String links = DataExtractJob.downloadLinks(99, DataExtractFiles.DOWNLOADS, false, "https://x.org");
        assertFalse(links, links.contains("all.zip") || links.contains("the file to send"));
        assertEquals(6, links.split("NOT VERIFIED, do not send", -1).length - 1);
        String ok = DataExtractJob.downloadLinks(99, DataExtractFiles.DOWNLOADS, true, "https://x.org");
        assertTrue(ok, ok.startsWith("<li><a class=\"extract-file\" href=\"https://x.org/dataExtractFile.do?job=99&amp;file=all.zip\">Everything in one zip file</a> (the file to send)</li>"));
        assertFalse(ok, ok.contains("NOT VERIFIED"));
        // the download action gives the zip only for a verified extract (its source: the list is empty otherwise)
    }

    /** Review L6: half-written files stayed until an admin opened the page; a clean-up could meet a job that just ended. */
    public void testUnfinishedExtractsAreTidiedButNotTheRunningOne() throws Exception {
        File stopped = DataExtractFiles.create(root, LocalDate.of(2026, 11, 9), 51);
        DataExtractFiles.writeRequest(stopped, 51, 7, "bfisher", "", new DataExtract.Request(Arrays.asList("Mexico"), ""));
        Files.write(new File(stopped, DataExtract.SPECIMENS + DataExtract.PART).toPath(), new byte[1000]);
        Files.write(new File(stopped, DataExtract.GENUS_SUMMARY).toPath(), new byte[10]);
        File running = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 52);
        Files.write(new File(running, DataExtract.SPECIMENS + DataExtract.PART).toPath(), new byte[1000]);
        File done = finished(LocalDate.of(2026, 11, 10), 53, true);
        assertEquals(1, DataExtractFiles.removeUnfinished(root, id -> id == 52));
        assertEquals("[request.json]", Arrays.toString(stopped.list()));
        assertEquals("the running extract keeps its file", "[specimens.csv.gz.part]", Arrays.toString(running.list()));
        assertEquals(7, done.list().length);
        assertEquals(0, DataExtractFiles.removeUnfinished(root, id -> id == 52));
        // an extract that finishes between the list and the check (its job is no longer active): read again, kept
        File racing = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 54);
        Files.write(new File(racing, DataExtract.SPECIMENS).toPath(), new byte[1000]);
        assertEquals(1, DataExtractFiles.removeUnfinished(root, id -> {
            if (id == 54) {
                try {
                    Files.write(new File(racing, DataExtract.MANIFEST_JSON).toPath(), "{\"specimens\": 1, \"verified\": true}".getBytes(StandardCharsets.UTF_8));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return false;
        }));   // 52 is tidied now (not active any more); 54 finished in between and keeps its files
        assertTrue(new File(racing, DataExtract.SPECIMENS).exists());
        assertEquals("[]", Arrays.toString(running.list()));
    }

    /** Review L5 and L10: the disk limit, and a note that could forge a line of admin.log. */
    public void testTheDiskLimitAndTheNoteOnOneLine() throws Exception {
        assertEquals("for Jack fake line of the log x", DataExtractJob.cleanNote(" for Jack\r\nfake line\tof the log\u0000 x "));
        assertEquals("", DataExtractJob.cleanNote(null));
        assertEquals("plain", DataExtractJob.cleanNote("plain"));
        finished(LocalDate.of(2026, 11, 10), 61, true);
        long used = DataExtractFiles.totalBytes(root);
        assertTrue(used > 0 && used < 1000);
        assertNull(DataExtractJob.diskRefusal(root, 1));
        assertNull("a root that does not exist yet", DataExtractJob.diskRefusal(new File(root, "not/yet"), 1));
        String noSpace = DataExtractJob.diskRefusal(root, Long.MAX_VALUE);
        assertTrue(noSpace, noSpace.startsWith("The server has less than "));
        // the extracts over their limit (a file of that length that takes no room on the disk)
        File big = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 62);
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(new File(big, DataExtract.SPECIMENS + DataExtract.PART), "rw")) {
            f.setLength(DataExtractFiles.MAX_TOTAL_BYTES);
        }
        assertTrue(DataExtractFiles.totalBytes(root) >= DataExtractFiles.MAX_TOTAL_BYTES);
        String over = DataExtractJob.diskRefusal(root, 1);
        assertTrue(over, over.startsWith("The extracts kept on the server use 5,120 MB (the limit is 5,120 MB). Delete one"));
        DataExtractFiles.delete(big);
        assertNull(DataExtractJob.diskRefusal(root, 1));
        assertEquals("caf" + (char) 0xe9, DataExtract.unhex("636166C3A9"));
        assertEquals("", DataExtract.unhex(""));
    }

    // ------------------------------------------------------------------ the pages

    public void testTheJobResultSaysVerifiedOrNotAndEscapes() {
        String text = "AntWeb specimen extract\n\nCountries: Côte d'Ivoire <b>\nCheck against the database: VERIFIED, every count equal\n";
        DataExtract.Result ok = new DataExtract.Result(manifest(), text, true, 1234567,
            Arrays.asList("specimens.csv.gz", "genus_summary.csv", "manifest.json", "manifest.txt"));
        String html = DataExtractJob.resultHtml(55, ok, "for <script>alert(1)</script> & \"co\"", "https://x.org");
        assertTrue(html, html.startsWith("<p id=\"extract-check\" data-verified=\"true\"><span class=\"aw-ok\">VERIFIED</span> 1,234,567 specimens."));
        assertTrue(html, html.contains("Note: for &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;co&quot;</p>"));
        assertTrue(html, html.contains("Countries: Côte d&#39;Ivoire &lt;b&gt;\n"));
        assertFalse(html, html.contains("<script>"));
        assertTrue(html, html.contains("href=\"https://x.org/dataExtractFile.do?job=55&amp;file=all.zip\""));
        assertTrue(html, html.contains("href=\"https://x.org/dataExtractFile.do?job=55&amp;file=specimens.csv.gz\">specimens.csv.gz</a>"));
        assertTrue(html, html.contains("file=manifest.txt\""));
        assertFalse("a file the extract does not have is not linked", html.contains("file=bioregion_list.csv"));
        assertFalse(html, html.contains("request.json"));
        // the job page shows it as it is (not taken for a whole page), and Server Status gets one plain line
        assertFalse(JobSummary.isFullPage(html));
        assertEquals(html, JobSummary.display(html));
        assertTrue(JobSummary.shortLine(html, false, 80), JobSummary.shortLine(html, false, 80).startsWith("VERIFIED 1,234,567 specimens. The file holds exactly"));

        DataExtract.Result different = new DataExtract.Result(manifest(), text, false, 5, Arrays.asList("specimens.csv.gz"));
        String bad = DataExtractJob.resultHtml(56, different, "", "https://x.org");
        assertTrue(bad, bad.startsWith("<p id=\"extract-check\" data-verified=\"false\"><span class=\"aw-bad\">NOT VERIFIED</span> 5 specimens were written"));
        assertTrue(bad, bad.contains("Do not send these files: start the extract again."));
        assertFalse("no zip of an extract that is not verified", bad.contains("all.zip"));
        assertTrue(bad, bad.contains("file=specimens.csv.gz\">specimens.csv.gz</a>: <b>NOT VERIFIED, do not send</b>"));
        // review M3: a requested country without specimens and the old names included are said on the page
        assertTrue(html, html.contains("<p class=\"aw-warn\" id=\"extract-empty\">AntWeb has no specimen for: <b>Côte d&#39;Ivoire</b>."));
        // Brian Fisher, 2026-11-11: the islands of the countries are not in the file, and the page says which
        assertTrue(html, html.contains("<p id=\"extract-islands-left-out\">Not in this extract: <b>Hawaii</b>. An island that is its own country in AntWeb"
            + " is not part of the country it belongs to politically. To have it in the file, start an extract that names it with the countries.</p>"));
        Map<String, Object> noIslands = manifest();
        noIslands.put("islands_left_out", new ArrayList<String>());
        assertFalse(DataExtractJob.resultHtml(58, new DataExtract.Result(noIslands, text, true, 1, Arrays.asList("manifest.txt")), "", "").contains("extract-islands-left-out"));
        assertTrue(html, html.contains("Specimens still filed under an old country name are included: U.S.A. (now United States), USA (now United States). Specimens filed that way: 7."));
        assertFalse(bad, bad.contains("extract-note"));

        // a very long manifest text is cut at a line end and still fits the job's result (60,000 bytes)
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 3000; i++) longText.append("  Island number ").append(i).append("                    1\n");
        String cut = DataExtractJob.resultHtml(57, new DataExtract.Result(manifest(), longText.toString(), true, 3000, Arrays.asList("manifest.txt")), "", "https://x.org");
        assertTrue(cut.getBytes(StandardCharsets.UTF_8).length < 60000);
        assertTrue(cut, cut.contains("The text is longer; all of it is in manifest.txt."));
        assertTrue(cut, cut.contains("1\n</pre>"));

        // review of #355: the cut is by bytes after escaping, so accents and quotes cannot push the result past its size
        StringBuilder quoted = new StringBuilder();
        for (int i = 0; i < 3000; i++) quoted.append("  C\u00f4te d'Ivoire \"").append(i).append("\" \u00e9\u00e9\u00e9\u00e9\u00e9    1\n");
        String shown = DataExtractJob.fitText(quoted.toString(), DataExtractJob.MAX_TEXT_SHOWN);
        assertTrue(shown.getBytes(StandardCharsets.UTF_8).length <= DataExtractJob.MAX_TEXT_SHOWN);
        assertTrue(shown, shown.endsWith("1\n") && shown.contains("&#39;") && shown.contains("&quot;"));
        String quotedCut = DataExtractJob.resultHtml(57, new DataExtract.Result(manifest(), quoted.toString(), true, 3000, Arrays.asList("manifest.txt")), "", "https://x.org");
        assertTrue(quotedCut.getBytes(StandardCharsets.UTF_8).length < 60000);
        assertTrue(quotedCut, quotedCut.contains("The text is longer; all of it is in manifest.txt."));
        assertEquals("a short text is shown whole, escaped", "a &lt;b&gt;\n", DataExtractJob.fitText("a <b>\n", 100));
    }

    public void testTheFormKeepsAndEscapesWhatWasTyped() {
        String form = DataExtractAction.formHtml("<input type=\"hidden\" name=\"_csrf\" value=\"t\">", "Mexico, \"><script>x</script>", Arrays.asList("Canada"),
            "nearctic", false, false, "for <Jack>", Arrays.asList("Canada", "Cote d'Ivoire", "Mexico"), Arrays.asList("Hawaii (United States)", "Gal<apagos (Ecuador)"),
            Arrays.asList("Nearctic", "Neotropical"), "https://x.org");
        assertTrue(form, form.contains("<form class=\"aw-form\" method=\"post\" action=\"https://x.org/dataExtract.do\" id=\"data-extract-form\"><input type=\"hidden\" name=\"_csrf\" value=\"t\">"));
        assertTrue(form, form.contains("value=\"Mexico, &quot;&gt;&lt;script&gt;x&lt;/script&gt;\""));
        assertFalse(form, form.contains("<script>"));
        assertTrue(form, form.contains("<option value=\"Canada\" selected>Canada</option>"));
        assertTrue(form, form.contains("<option value=\"Cote d&#39;Ivoire\">Cote d&#39;Ivoire</option>"));
        assertTrue(form, form.contains("<option value=\"Nearctic\" selected>Nearctic</option>"));
        // Brian Fisher, 2026-11-11: no box that brings a country's islands with it; the form says how an island is asked for
        assertFalse(form, form.contains("name=\"islands\""));
        assertFalse(form, form.contains("Include islands"));
        assertTrue(form, form.contains("<p class=\"aw-note\" id=\"extract-islands-note\"><b>Islands are countries of their own.</b> An extract of a country does not hold the"
            + " specimens of an island AntWeb treats as its own country: name the island with the countries to have it in the file."));
        assertTrue(form, form.contains("AntWeb's island countries, each with the country it belongs to politically: Hawaii (United States), Gal&lt;apagos (Ecuador).</p>"));
        assertTrue(DataExtractAction.HELP, DataExtractAction.HELP.contains("an extract of United States has no specimen of Hawaii. To have an island in the file, name it with the countries."));
        assertTrue(form, form.contains("value=\"for &lt;Jack&gt;\""));
        assertTrue(form, form.contains("<details id=\"extract-country-list\" open>"));
        // ants only unless the box is ticked; the file of the specimens without a country only when asked for
        assertTrue(form, form.contains("<input type=\"checkbox\" name=\"allFamilies\" value=\"on\"> Include specimens that are not ants</label>"));
        assertTrue(form, form.contains("<input type=\"checkbox\" name=\"noCountry\" value=\"on\"> Also a file of the specimens that have no country</label>"));
        String ticked = DataExtractAction.formHtml("", "", new ArrayList<String>(), "", true, true, "", Arrays.asList("Canada"), new ArrayList<String>(), Arrays.asList("Nearctic"), "");
        assertTrue(ticked, ticked.contains("name=\"allFamilies\" value=\"on\" checked>") && ticked.contains("name=\"noCountry\" value=\"on\" checked>"));
        String empty = DataExtractAction.formHtml("", "", new ArrayList<String>(), "", false, false, "", Arrays.asList("Canada"), new ArrayList<String>(), Arrays.asList("Nearctic"), "");
        assertTrue("no island country in the database: the note without a list", empty.contains("that were left out.</p>"));
        assertTrue(empty, empty.contains("<details id=\"extract-country-list\">"));
        assertFalse(empty, empty.contains(" selected"));
        for (String page : new String[] {form, DataExtractAction.HELP}) {
            assertFalse("user-facing text says daily update, never nightly", page.toLowerCase().contains("nightly"));
            assertFalse("no em-dash", page.contains("\u2014"));
        }
    }

    public void testTheListOfExtractsLinksOnlyFinishedOnes() throws Exception {
        finished(LocalDate.of(2026, 11, 9), 41, true);
        finished(LocalDate.of(2026, 11, 10), 42, false);
        File running = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 43);
        DataExtractFiles.writeRequest(running, 43, 7, "bfisher", "", new DataExtract.Request(Arrays.asList("Mexico"), "", true, true));
        File failed = DataExtractFiles.create(root, LocalDate.of(2026, 11, 10), 44);
        DataExtractFiles.writeRequest(failed, 44, 7, "bfisher", "", new DataExtract.Request(Arrays.asList("Canada"), ""));
        String html = DataExtractAction.listHtml(DataExtractFiles.list(root), 43, "<input type=\"hidden\" name=\"_csrf\" value=\"t\">", "https://x.org");
        assertTrue(html, html.contains("<h2>Extracts on the server (4)</h2>"));
        assertTrue(html, html.contains("<tr id=\"extract-41\"><td>2026-11-09</td><td>bfisher</td><td>United States, Canada; not in the file: Hawaii; ants only; species list of Nearctic</td>"
            + "<td>for the &quot;Nearctic&quot; &lt;team&gt;</td><td class=\"aw-num\">1,234</td><td><span class=\"aw-ok\">VERIFIED</span></td>"));
        assertTrue(html, html.contains("dataExtractFile.do?job=41&amp;file=all.zip"));
        assertTrue(html, html.contains("<tr id=\"extract-42\">") && html.contains("<span class=\"aw-bad\">NOT VERIFIED</span> do not send"));
        assertTrue(html, html.contains("<td>Mexico; all families; with the no-country file</td><td></td><td class=\"aw-num\"></td><td>in progress</td><td></td>"));
        assertTrue(html, html.contains("<td>Canada; ants only</td><td></td><td class=\"aw-num\"></td><td><span class=\"aw-bad\">did not finish</span></td><td></td>"));
        assertFalse(html, html.contains("job=43&amp;file") || html.contains("job=44&amp;file"));
        assertTrue(html, html.contains("href=\"https://x.org/jobStatus.do?id=44\">44</a>"));
        assertTrue(DataExtractAction.listHtml(new ArrayList<DataExtractFiles.Entry>(), 0, "", "").contains("None yet."));
        // review L2: an extract that is not verified has no zip to send, and every link says so
        String row42 = html.split("<tr id=\"extract-42\">", 2)[1].split("</tr>", 2)[0];
        assertFalse(row42, row42.contains("all.zip") || row42.contains("the file to send"));
        assertTrue(row42, row42.contains("file=specimens.csv.gz\">specimens.csv.gz</a>: <b>NOT VERIFIED, do not send</b>"));
        // a Delete button (POST with the form token) for every extract but the one in progress
        assertTrue(html, html.contains("<form class=\"aw-inline-form\" method=\"post\" action=\"https://x.org/dataExtract.do\"><input type=\"hidden\" name=\"_csrf\" value=\"t\">"
            + "<input type=\"hidden\" name=\"op\" value=\"delete\"><input type=\"hidden\" name=\"job\" value=\"41\">"));
        assertFalse(html, html.contains("name=\"job\" value=\"43\""));
        assertTrue(html, html.contains("name=\"job\" value=\"44\""));
    }
}

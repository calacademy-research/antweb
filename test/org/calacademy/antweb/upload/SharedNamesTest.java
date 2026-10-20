package test.org.calacademy.antweb.upload;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.SharedNames;

/**
 * Names shared by two or more AntCat taxa (Brian Fisher, 2026-10-20): the nightly name update does not
 * move their specimens and asks the curator. Rows of the AntCat export of 2026-10-20
 * (fixtures/antcat-shared-names.tsv; the export's columns, fewer of them).
 *
 * Run with -Dantweb.test=<the test/ folder> (default "test", the repository's).
 */
public class SharedNamesTest extends TestCase {

    static SharedNames fixture() throws IOException {
        return SharedNames.fromExport(Paths.get(System.getProperty("antweb.test", "test"),
            "org/calacademy/antweb/upload/fixtures/antcat-shared-names.tsv").toFile());
    }

    public void testTwoObsoleteCombinations() throws IOException {
        // The import keeps only the first of these two rows and stores nothing of the second.
        SharedNames s = fixture();
        assertEquals(List.of("Cryptopone sauteri", "Ectomomyrmex sauteri"), s.candidatesOf("Pachycondyla sauteri"));
        assertEquals(List.of("Brachyponera solitaria", "Diacamma solitaria"), s.candidatesOf("Pachycondyla solitaria"));
        assertEquals("case and subgenus do not matter", List.of("Cryptopone sauteri", "Ectomomyrmex sauteri"),
            s.candidatesOf("pachycondyla (Bothroponera) sauteri"));
        assertEquals("name is shared by two taxa; please confirm the identification: Cryptopone sauteri or Ectomomyrmex sauteri",
            SharedNames.reason(s.candidatesOf("Pachycondyla sauteri")));
    }

    public void testHomonymAndValidNames() throws IOException {
        SharedNames s = fixture();
        assertEquals("a junior homonym without a valid name: name, author, status",
            List.of("Gnamptogenys andina Longino et al., 2026 (homonym)", "Holcoponera andina"), s.candidatesOf("Gnamptogenys andina"));
        assertEquals("three taxa", List.of("Camponotus piceus", "Formica picea", "Nylanderia vividula"), s.candidatesOf("Formica picea"));
        assertEquals("name is shared by three taxa; please confirm the identification: Camponotus piceus, Formica picea or Nylanderia vividula",
            SharedNames.reason(s.candidatesOf("Formica picea")));
        assertEquals(List.of("Camponotus cruentatus", "Dinomyrmex gigas"), s.candidatesOf("Camponotus gigas"));
    }

    public void testNotShared() throws IOException {
        SharedNames s = fixture();
        assertNull("one row", s.candidatesOf("Cryptopone sauteri"));
        assertNull("two rows, one valid name", s.candidatesOf("Crematogaster obscurus"));
        assertNull("an unavailable name is not a taxon", s.candidatesOf("Protanilla wallacei"));
        assertNull("not in AntCat", s.candidatesOf("Pheidole zzz"));
        assertNull(s.candidatesOf(null));
        assertEquals(0, SharedNames.none().size());
        assertEquals("only species and below", 0, SharedNames.fromExport(new StringReader(
            "antcat id\tsubfamily\tgenus\tspecies\tsubspecies\tstatus\tcurrent valid name\n"
            + "1\tPonerinae\tPachycondyla\t\t\tvalid\t\n2\tPonerinae\tPachycondyla\t\t\tsynonym\tPonerinae Ponera\n")).size());
        try {
            SharedNames.fromExport(new StringReader("a\tb\n1\t2\n"));
            fail("not an export");
        } catch (IOException expected) {
            // the caller falls back to the homonym table
        }
        assertFalse(new File("no-such-export.txt").isFile());
    }

    public void testFromTheHomonymTable() {
        SharedNames s = SharedNames.of(Map.of(
            "Carebara silvestrii", List.of("Carebara silvestrii (unrecognized)", "Carebara silvestrii Forel, 1905 (homonym)"),
            "Pheidole one", List.of("Pheidole one", "Pheidole one")));
        assertEquals(2, s.candidatesOf("Carebara silvestrii").size());
        assertNull("the same candidate twice is not shared", s.candidatesOf("Pheidole one"));
    }
}

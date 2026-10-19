package test.org.calacademy.antweb;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import junit.framework.TestCase;
import org.apache.commons.beanutils.PropertyUtils;
import org.calacademy.antweb.Genus;
import org.calacademy.antweb.Species;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.Subfamily;
import org.calacademy.antweb.Subspecies;
import org.calacademy.antweb.Taxon;

/**
 * The image picker (Pick / Unpick on images.do) and the taxon properties JSPs read.
 *
 * Reported 2026-10-19: picking casent0852727 (Camponotus singularis, the first specimen on
 * images.do?genus=camponotus&species=singularis&rank=species) showed
 *   "Taxon not found for family:null subfamily:null genus:false species:singularis ..."
 * The Pick button was inside the page's getComparison form, so the browser submitted that form
 * (name=singularis, rank=species, genus=false) instead of the pick. genus was "false" because
 * Taxon had boolean isGenus() next to String getGenus(): the JavaBeans property "genus" then reads
 * isGenus(), and <bean:write property="genus"/> printed "false".
 *
 * Runs in CI inside the antweb image (.github/workflows/tests.yaml), with junit.textui.TestRunner.
 */
public class ImagePickerTest extends TestCase {

    private static Species singularis() {
        Species s = new Species();
        s.setRank("species");
        s.setFamily("formicidae");
        s.setSubfamily("formicinae");
        s.setGenus("camponotus");
        s.setSpecies("singularis");
        return s;
    }

    /** The failing input: bean:write of showTaxon.genus on the species page. */
    public void testBeanPropertyGenusIsTheGenusName() throws Exception {
        Species s = singularis();
        assertEquals("camponotus", PropertyUtils.getProperty(s, "genus"));
        assertEquals("formicinae", PropertyUtils.getProperty(s, "subfamily"));
        assertEquals("singularis", PropertyUtils.getProperty(s, "species"));
        assertEquals("species", PropertyUtils.getProperty(s, "rank"));
        assertEquals("singularis", PropertyUtils.getProperty(s, "name"));
    }

    /** No taxon class may have a boolean reader for a name field (subfamily, genus, species, subspecies). */
    public void testNameFieldsAreStringProperties() throws Exception {
        Class<?>[] classes = { Taxon.class, Subfamily.class, Genus.class, Species.class, Subspecies.class, Specimen.class };
        for (Class<?> c : classes) {
            for (PropertyDescriptor p : Introspector.getBeanInfo(c).getPropertyDescriptors()) {
                String n = p.getName();
                if (n.equals("family") || n.equals("subfamily") || n.equals("genus") || n.equals("species") || n.equals("subspecies")) {
                    assertNotNull(c.getSimpleName() + "." + n + " has no reader", p.getReadMethod());
                    assertEquals(c.getSimpleName() + "." + n + " is read by " + p.getReadMethod(),
                        String.class, p.getReadMethod().getReturnType());
                }
            }
        }
    }

    public void testRankChecksStillWork() {
        assertTrue(singularis().isSpeciesRank());
        assertFalse(singularis().isGenusRank());
        assertTrue(new Genus().isGenusRank());
        assertTrue(new Subfamily().isSubfamilyRank());
        assertTrue(new Subspecies().isSubspeciesRank());
    }

    /**
     * The Pick and Unpick buttons are POST forms (Csrf.postButton). They are drawn by
     * taxonChildImageSet.jsp, which taxonChildImages.jsp includes; no form may be open there.
     * Checked when the JSP sources are available (ANTWEB_WEB_DIR, or ./web from the repository).
     */
    public void testPickButtonsAreNotInsideAnotherForm() throws Exception {
        Path web = Paths.get(System.getProperty("antweb.web", System.getenv().getOrDefault("ANTWEB_WEB_DIR", "web")));
        Path jsp = web.resolve("taxonChildImages.jsp");
        if (!Files.exists(jsp)) return;
        String s = Files.readString(jsp);
        int include = s.indexOf("include file=\"/taxonChildImageSet.jsp\"");
        assertTrue("include of taxonChildImageSet.jsp not found", include > 0);
        String before = s.substring(0, include).toLowerCase();
        int open = count(before, "<form");
        int close = count(before, "</form");
        assertEquals("a form is still open where the Pick buttons are drawn", open, close);
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++;
        return n;
    }
}

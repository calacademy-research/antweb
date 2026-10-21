package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;

import org.calacademy.antweb.util.AntwebUtil;

/**
 * "Specimen files uploaded before" on the Curate page (uploadHistory.do, 2026-10-20): the archived
 * specimen files of one group in web/upload/ are its AntWeb, TaxonWorks and GBIF files, and no other
 * group's (group 1 is not group 12). The list used to leave out the TaxonWorks and GBIF files.
 */
public class UploadHistoryFilesTest extends TestCase {

    public void testAntwebTaxonWorksAndGbifFilesOfTheGroup() {
        assertTrue(AntwebUtil.isGroupSpecimenFile("20220224-11:49:08-specimen2.txt", "specimen2"));
        assertTrue(AntwebUtil.isGroupSpecimenFile("20261014-05:31:02-specimenTW1.txt", "specimen1"));
        assertTrue(AntwebUtil.isGroupSpecimenFile("20240101-10:00:00-specimenGBIF2.txt", "specimen2"));
    }

    public void testOtherGroupsAndOtherFilesAreLeftOut() {
        assertFalse("group 12 is not group 1", AntwebUtil.isGroupSpecimenFile("20220224-11:49:08-specimen12.txt", "specimen1"));
        assertFalse(AntwebUtil.isGroupSpecimenFile("20261014-05:31:02-specimenTW12.txt", "specimen1"));
        assertFalse("worldants files are not specimen files", AntwebUtil.isGroupSpecimenFile("20261014-05:00:00-worldants.txt", "specimen1"));
        assertFalse("nothing chosen", AntwebUtil.isGroupSpecimenFile("20220224-11:49:08-specimen2.txt", "none"));
        assertFalse("short name", AntwebUtil.isGroupSpecimenFile("specimen2.txt", "specimen2"));
        assertFalse("not a text file", AntwebUtil.isGroupSpecimenFile("20220224-11:49:08-specimen2.zip", "specimen2"));
        assertFalse(AntwebUtil.isGroupSpecimenFile(null, "specimen2"));
        assertFalse(AntwebUtil.isGroupSpecimenFile("20220224-11:49:08-specimen2.txt", null));
    }
}

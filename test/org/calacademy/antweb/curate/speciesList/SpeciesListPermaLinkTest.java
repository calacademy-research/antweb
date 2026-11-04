package test.org.calacademy.antweb.curate.speciesList;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.speciesList.SpeciesListToolProps;

/**
 * The Species List Tool's [permalink] (2026-10-27): the list names in it come from the request, and the link is a
 * single-quoted attribute. A quote or an angle bracket in a name must not end the attribute.
 */
public class SpeciesListPermaLinkTest extends TestCase {

    public void testListNamesAreEscaped() {
        SpeciesListToolProps props = new SpeciesListToolProps();
        props.setMapSpeciesList1Name("a'b\"c<d>");
        String tag = props.getPermaLinkTag();
        assertTrue(tag, tag.startsWith("<a href='"));
        assertTrue(tag, tag.endsWith("'>[permalink]</a>"));
        assertTrue(tag, tag.contains("mapSpeciesList1Name=a&#39;b&quot;c&lt;d&gt;"));
        String inside = tag.substring("<a href='".length(), tag.length() - "'>[permalink]</a>".length());
        assertFalse(inside, inside.contains("'") || inside.contains("<") || inside.contains("\""));
    }

    public void testPlainNamesUnchanged() {
        SpeciesListToolProps props = new SpeciesListToolProps();
        props.setMapSpeciesList1Name("madagascarants");
        assertTrue(props.getPermaLinkTag(), props.getPermaLinkTag().contains("mapSpeciesList1Name=madagascarants&amp;"));
    }

    /** "(indet)" names are not added to a place's list (2026-11-03); morphospecies and valid names are. */
    public void testIndetRefusedOnPlaceList() {
        assertTrue(org.calacademy.antweb.home.SpeciesListDb.refusesOnPlaceList("dorylinaelividopone (indet)"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.refusesOnPlaceList("ponerinaeanochetus casc_mz01"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.refusesOnPlaceList("ponerinaeanochetus madagascarensis"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.refusesOnPlaceList(null));
    }

    /** Subfamily links: ant subfamilies only (Brian Fisher, 2026-11-04: Madagascar showed Acridinae, Apinae, Mantinae...). */
    public void testSubfamilyLinksAreAntSubfamiliesOnly() {
        assertTrue(org.calacademy.antweb.home.SpeciesListDb.isAntSubfamily("formicidae", "dorylinae"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.isAntSubfamily("acrididae", "acridinae"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.isAntSubfamily("apidae", "apinae"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.isAntSubfamily(null, "mantinae"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.isAntSubfamily("formicidae", "(formicidae)"));
        assertFalse(org.calacademy.antweb.home.SpeciesListDb.isAntSubfamily("formicidae", null));
    }
}

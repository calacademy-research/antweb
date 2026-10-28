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
}

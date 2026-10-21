package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.util.NamedQuery;
import org.calacademy.antweb.util.Queries;

/**
 * The "Relevant Queries" boxes of the Upload groups page (viewGroups.do) and the Login Manager
 * (viewLogins.do): each query has a one-line plain explanation next to it, and each query's column
 * headings match its columns (uploadingCurators had no "Group ID" heading, so the headings from
 * "Is Admin?" on stood over the wrong column). Brian Fisher, 2026-10-21. The "groups" query (every
 * upload group with its last upload date) was removed the same day: the page itself lists the groups.
 */
public class RelevantQueriesTest extends TestCase {

    static final String[] NAMES = {"specimenGroups", "uploadingCurators", "curatorEmails"};

    public void testEachQueryIsExplained() {
        String groups = Queries.getQueryList("Groups");
        String logins = Queries.getQueryList("Login");
        for (String[] box : new String[][] {{groups, "specimenGroups"}, {logins, "uploadingCurators", "curatorEmails"}}) {
            for (int i = 1; i < box.length; i++) {
                String name = box[i];
                int at = box[0].indexOf("name=" + name + "'>" + name + "</a>");
                assertTrue(name + ": " + box[0], at >= 0);
                String rest = box[0].substring(at);
                int next = rest.indexOf("<li>");
                String item = next < 0 ? rest : rest.substring(0, next);
                assertTrue(name + " has no explanation: " + item, item.contains("</a> <span class=\"query-help\">"));
                assertTrue(item, item.length() > 80);
            }
        }
        assertFalse("the groups query is removed", groups.contains("name=groups'"));
        assertFalse(groups, groups.contains("date of the last specimen upload"));
        assertNull("the groups query is removed", Queries.getNamedQuery("groups"));
        assertTrue(logins, logins.contains("These are not the uploaders: see uploadingCurators for those."));
        assertTrue("escaped", groups.contains("a group&#39;s total"));
        assertFalse("other boxes unchanged", Queries.getQueryList("Geolocale").contains("query-help"));
    }

    public void testHeadingsMatchTheColumns() {
        for (String name : NAMES) {
            NamedQuery q = Queries.getNamedQuery(name);
            assertNotNull(name, q);
            String select = q.getQuery().toLowerCase();
            // Columns up to the outer " from "; commas inside parentheses (sub-selects, concat()) do not count.
            int depth = 0, count = 1;
            for (int i = "select ".length(); i < select.length(); i++) {
                char c = select.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == ',' && depth == 0) count++;
                else if (depth == 0 && select.startsWith(" from ", i)) break;
            }
            int headings = q.getHeaderHtml().split("<th>", -1).length - 1;
            assertEquals(name + ": " + q.getHeaderHtml() + " / " + q.getQuery(), count, headings);
        }
    }
}

package test.org.calacademy.antweb.util;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.calacademy.antweb.search.SearchAction;
import org.calacademy.antweb.util.OverviewMgr;
import org.calacademy.antweb.util.ProjectMgr;
import org.calacademy.antweb.util.AntwebException;

/**
 * Low findings of the public pages (docs/flows/public-pages-findings.md), fixed 2026-10-26. No database.
 * F3:  geolocaleId that is not a number is "Geolocale not found" (AntwebException, a message), not a
 *      NumberFormatException (the error page).
 * F4:  old bioregion project links (project=africanants, ...) are redirected with bioregionName=..., not a bare word.
 * F13: the temporary specimen search limit (administrators, nightly maps) is per thread, not for every visitor.
 */
public class PublicPagesLowFindingsTest extends TestCase {

    private static HttpServletRequest request(Map<String, String> params) {
        return (HttpServletRequest) Proxy.newProxyInstance(PublicPagesLowFindingsTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> {
                if ("getParameter".equals(method.getName())) return params.get((String) args[0]);
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                if (method.getReturnType() == long.class) return 0L;
                return null;
            });
    }

    public void testGeolocaleIdNotANumberIsNotFound() {
        Map<String, String> params = new HashMap<>();
        params.put("geolocaleId", "abc");
        try {
            OverviewMgr.findOverview(request(params));
            fail("no AntwebException for geolocaleId=abc");
        } catch (AntwebException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Geolocale not found for geolocaleId:abc"));
        }
    }

    public void testOldBioregionProjectsKeepTheirFilter() {
        String[][] cases = {
            {"africanants", "Afrotropical"}, {"eurasianants", "Palearctic"}, {"nearcticants", "Nearctic"},
            {"pacificislandsants", "Oceania"}, {"neotropicalants", "Neotropical"}, {"australianants", "Australasia"},
            {"southeastasiaants", "Indomalaya"}, {"malagasyants", "Malagasy"}
        };
        for (String[] c : cases) {
            String target = "https://www.antweb.org/browse.do?genus=mystrium&rank=genus&project=" + c[0];
            assertEquals(c[0], "https://www.antweb.org/browse.do?genus=mystrium&rank=genus&bioregionName=" + c[1],
                ProjectMgr.movedBioregionTarget(target));
        }
        assertNull(ProjectMgr.movedBioregionTarget("https://www.antweb.org/browse.do?genus=mystrium&project=californiaants"));
        assertNull(ProjectMgr.movedBioregionTarget(null));
    }

    public void testTempSearchLimitIsPerThread() throws Exception {
        try {
            SearchAction.setTempSpecimenSearchLimit(SearchAction.noSpecimenSearchLimit);
            assertTrue(SearchAction.isTempSpecimenSearchLimit());
            final boolean[] other = {true};
            Thread t = new Thread(() -> other[0] = SearchAction.isTempSpecimenSearchLimit());
            t.start();
            t.join();
            assertFalse("another request thread sees the raised limit", other[0]);
        } finally {
            SearchAction.undoSetTempSpecimenSearchLimit();
        }
        assertFalse(SearchAction.isTempSpecimenSearchLimit());
    }
}

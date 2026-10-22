package test.org.calacademy.antweb.maps;

import java.util.ArrayList;

import junit.framework.TestCase;

import org.calacademy.antweb.Map;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.util.HttpUtil;

/**
 * The map data Map.java writes for marker popups (2026-10-22, group.do?id=80): locality names and
 * codes form-encoded once (drawMap.js decodes them once; tests/unit/test_map_popups.py runs the
 * JavaScript), every value a safe JavaScript string, a missing value '' and not 'null'.
 * Static methods only: a Map instance reads the server settings (AntwebProps).
 */
public class MapPopupDataTest extends TestCase {

    private static Specimen specimen(String code, String localityName, String localityCode) {
        Specimen s = new Specimen();
        s.setCode(code);
        // As Map.setPoints(specimens, ...) stores them.
        if (localityName != null) s.setLocalityName(HttpUtil.encode(localityName));
        if (localityCode != null) s.setLocalityCode(HttpUtil.encode(localityCode));
        return s;
    }

    public void testLocalityArraysEncodedOnce() {
        ArrayList<Specimen> list = new ArrayList<>();
        list.add(specimen("casent1", "West Bengal, India", "loc22.8,88.3"));
        list.add(specimen("casent2", null, null));
        String names = Map.javaScriptSpecimenArray(list, "localityname");
        String codes = Map.javaScriptSpecimenArray(list, "localitycode");
        assertEquals("new Array('West+Bengal%2C+India','')", names);
        assertEquals("new Array('loc22.8%2C88.3','')", codes);
        assertFalse(codes.contains("%25"));
    }

    public void testJsStringEscapes() {
        assertEquals("''", Map.jsString(null));
        assertEquals("''", Map.jsString("null"));
        assertEquals("'O\\'Brien'", Map.jsString("O'Brien"));
        assertEquals("'a\\\\b'", Map.jsString("a\\b"));
        assertEquals("'\\u003c/script\\u003e'", Map.jsString("</script>"));
        assertEquals("'a\\nb'", Map.jsString("a\nb"));
    }
}

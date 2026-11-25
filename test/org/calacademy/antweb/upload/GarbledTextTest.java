package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.GarbledText;

/**
 * GarbledText.is(): the texts found as states on production on 25 Nov 2026 count, ordinary accented text does not.
 * The samples are written as escapes so the file reads the same in any editor.
 */
public class GarbledTextTest extends TestCase {

    public void testTheDamagedStatesOfProductionCount() {
        String[] garbled = {"Li\u221A\u00AEge", "San Luis Potos\uFFFD", "Michoac\uFFFDn", "Panam\u00B7", "Chiriqu\u00CC", "Rond\u00D9nia", "Pet\u00C8n", "Gal\u2021pagos", "S\u201Eo Paulo", "Solol\u00B7", "Kh\u00B7nh H\u00DAa", "Tucum\u00B7n", "Li\u00C3\u00A8ge", "Cura\u00C3\u00A7ao", "Alajuela: San Ram\u00DBn", "Tamaul\u00CCpas"};
        for (String text : garbled) {
            assertTrue(text, GarbledText.is(text));
        }
    }

    public void testOrdinaryTextDoesNotCount() {
        String[] fine = {"Li\u00E8ge", "LI\u00C8GE", "San Luis Potos\u00ED", "Panam\u00E1", "S\u00E3o Paulo", "Kh\u00E1nh H\u00F2a", "col\u00B7legi", "\u00CEle-de-France", "\u00C8ze", "C\u00F4te d'Ivoire", "Nu\u00F1oa", "5 km N of town", "", "\u221A2 m", "A\u00C7ORES"};
        for (String text : fine) {
            assertFalse(text, GarbledText.is(text));
        }
        assertFalse(GarbledText.is(null));
    }
}

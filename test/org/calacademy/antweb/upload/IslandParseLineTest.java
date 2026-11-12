package test.org.calacademy.antweb.upload;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.Genus;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.Subfamily;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.geolocale.Region;
import org.calacademy.antweb.upload.AntwebUpload;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.util.GeolocaleMgr;
import org.calacademy.antweb.util.GroupMgr;
import org.calacademy.antweb.util.TaxonMgr;

/**
 * The upload's own line parser (SpecimenUploadParse.parseLine) on records of islands that are their own country
 * (Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
 * specimens never count toward the political parent."). IslandCountryTest tests the rule (RecordPlace); this test runs
 * a file's header and lines through parseLine itself and reads the specimen it would store: country, island_country,
 * adm1, region, subregion, bioregion, and the red flag of the bounds check.
 *
 * No database. parseLine reads the places, taxa and groups AntWeb keeps in memory (GeolocaleMgr, TaxonMgr, GroupMgr);
 * the test puts production's place rows of 2026-11-11 there (the private caches, by reflection: there is no other way
 * in without a database) and puts back what was there afterwards. The upload object is made with no connection;
 * parseLine does not use one.
 *
 * Region and subregion (automatic review, 2026-11-11): the island row's value where it has one, otherwise the record
 * keeps the value of the country its file wrote, and the text "none" is never stored: the rule of
 * tools/place/island_countries.py for the specimens already stored (RecordPlace.rowValueOr).
 */
public class IslandParseLineTest extends TestCase {

    private static final String HEADER = "specimencode\tsubfamily\tgenus\tspecies\tcountry\tadm1\tloclatitude\tloclongitude";

    private final Map<String, Country> countries = new LinkedHashMap<>();
    private final MultiKeyMap<String, Adm1> adm1s = new MultiKeyMap<>();
    private final ArrayList<Adm1> adm1Rows = new ArrayList<>();
    private final Map<Field, Object> before = new LinkedHashMap<>();
    private Object upload;
    private Group group;
    private int line;

    private Country country(int id, String name, String region, String subregion, String politicalParent, boolean island, boolean valid,
                            String validName, String bioregion, String box) {
        Country c = new Country();
        c.setId(id);
        c.setName(name);
        c.setGeorank("country");
        c.setParent(subregion);
        c.setRegion(region);
        c.setSubregion(subregion);
        c.setCountry(politicalParent);
        c.setIsIsland(island);
        c.setIsValid(valid);
        c.setValidName(validName);
        c.setBioregion(bioregion);
        c.setBoundingBox(box);
        countries.put(name, c);
        return c;
    }

    private void adm1(int id, String name, String parent, boolean valid, String validName, String bioregion) {
        Adm1 a = new Adm1();
        a.setId(id);
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(parent);
        a.setIsValid(valid);
        a.setValidName(validName);
        a.setBioregion(bioregion);
        adm1s.put(name, parent, a);
        adm1Rows.add(a);
    }

    private void set(Class<?> owner, String field, Object value) throws Exception {
        Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        if (!before.containsKey(f)) before.put(f, f.get(null));
        f.set(null, value);
    }

    /** The places in memory, from the rows of this test (again after a test changed a row). */
    private void placesInMemory() throws Exception {
        ArrayList<Country> islands = new ArrayList<>();
        for (Country c : countries.values()) if (c.isIsland()) islands.add(c);
        ArrayList<Geolocale> all = new ArrayList<>(countries.values());
        all.addAll(adm1Rows);
        set(GeolocaleMgr.class, "s_regions", new ArrayList<Region>());
        set(GeolocaleMgr.class, "s_geolocales", all);
        set(GeolocaleMgr.class, "adm1List", adm1Rows);
        set(GeolocaleMgr.class, "countryNameMap", countries);
        set(GeolocaleMgr.class, "countrySpellingMap", GeolocaleMgr.countriesBySpelling(countries.values()));
        set(GeolocaleMgr.class, "adm1CountryMap", adm1s);
        set(GeolocaleMgr.class, "islands", islands);
    }

    /** Production's rows of 2026-11-11 (boxes: Hawaii's and the Galapagos' own; the United States' holds Hawaii). */
    @Override
    protected void setUp() throws Exception {
        country(97, "United States", "Americas", "North America", null, false, true, "none", "Nearctic", "-179.2, 18.9, -66.9, 71.4");
        country(103, "Ecuador", "Americas", "South America", null, false, true, null, "Neotropical", "-92.1, -5.0, -75.2, 1.7");
        country(9, "Mauritius", "Africa", "Eastern Africa", null, false, true, null, "Malagasy", null);
        country(423, "Hawaii", "Oceania", "Polynesia", "United States", true, true, "none", "Oceania", "-160.5, 18.9, -154.8, 22.3");
        country(1721, "Galapagos Islands", "Americas", "South America", "Ecuador", true, true, null, "Neotropical", "-92.05, -1.4101, -89.2402, 1.70");
        country(1732, "Galapagos", null, "South America", "Galapagos", true, false, "Galapagos Islands", "Neotropical", null);
        country(8864, "Galápagos", null, "South America", null, true, false, "Galapagos Islands", "Neotropical", null);
        country(12, "Reunion", "Africa", "Eastern Africa", "Reunion", false, true, null, "Malagasy", null);
        country(8865, "Rodrigues", "Africa", "Eastern Africa", null, false, true, null, "Malagasy", null);
        adm1(620, "Galapagos", "Ecuador", false, "Galapagos Islands", "Neotropical");
        adm1(2394, "Galápagos", "Ecuador", false, "Galapagos Islands", "Neotropical");
        adm1(1017, "Oahu", "United States", false, "Hawaii", "Nearctic");
        adm1(691, "Rodrigues", "Mauritius", false, "Rodrigues", "Malagasy");      // after the tool gave it its valid name
        adm1(446, "California", "United States", true, null, "Nearctic");
        placesInMemory();

        Subfamily myrmicinae = new Subfamily();
        myrmicinae.setFamily("formicidae");
        myrmicinae.setSubfamily("myrmicinae");
        myrmicinae.setStatus("valid");
        myrmicinae.setRank("subfamily");
        Genus pheidole = new Genus();
        pheidole.setFamily("formicidae");
        pheidole.setSubfamily("myrmicinae");
        pheidole.setGenus("pheidole");
        pheidole.setStatus("valid");
        pheidole.setRank("genus");
        HashMap<String, Taxon> genera = new HashMap<>();
        genera.put("myrmicinaepheidole", pheidole);
        HashMap<String, Taxon> taxa = new HashMap<>(genera);
        taxa.put("myrmicinae", myrmicinae);
        set(TaxonMgr.class, "s_subfamilies", new ArrayList<Taxon>(Arrays.asList(myrmicinae)));
        set(TaxonMgr.class, "s_genera", genera);
        set(TaxonMgr.class, "s_species", new HashMap<String, Taxon>());
        set(TaxonMgr.class, "s_taxa", taxa);

        group = new Group();
        group.setId(21);
        group.setName("Test group");
        set(GroupMgr.class, "s_groups", new ArrayList<Group>(Arrays.asList(group)));

        Constructor<?> make = Class.forName("org.calacademy.antweb.upload.SpecimenUpload").getDeclaredConstructor(java.sql.Connection.class);
        make.setAccessible(true);
        upload = make.newInstance((java.sql.Connection) null);
    }

    @Override
    protected void tearDown() throws Exception {
        for (Map.Entry<Field, Object> e : before.entrySet()) e.getKey().set(null, e.getValue());
    }

    private Method method(String name, Class<?>... types) throws Exception {
        for (Class<?> c = upload.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                // the class above
            }
        }
        throw new NoSuchMethodException(name);
    }

    /** One line of a file with HEADER, through the upload's own column mapping and parseLine: the specimen to store. */
    @SuppressWarnings("unchecked")
    private Hashtable<String, Object> parse(String country, String adm1, String lat, String lon) throws Exception {
        ArrayList<String> columns = (ArrayList<String>) method("getSpecimenColumns", String.class).invoke(upload, HEADER);
        ArrayList<String> others = (ArrayList<String>) method("getOtherColumns", String.class).invoke(upload, HEADER);
        Login curator = new Login();
        curator.setGroupId(group.getId());
        Hashtable<String, Object> specimen = new Hashtable<>();
        ++line;
        String theLine = "test" + String.format("%07d", line) + "\tmyrmicinae\tpheidole\tmegacephala\t" + country + "\t" + adm1 + "\t" + lat + "\t" + lon;
        Object error = method("parseLine", String.class, int.class, Hashtable.class, Hashtable.class, ArrayList.class, ArrayList.class,
            String.class, Login.class).invoke(upload, theLine, line, specimen, new Hashtable<String, Object>(), others, columns, "islands.txt", curator);
        assertNull(country + " + " + adm1 + ": " + error, error);
        assertEquals("myrmicinaepheidole megacephala", specimen.get("taxon_name"));
        return specimen;
    }

    private Hashtable<String, Object> parse(String country, String adm1) throws Exception {
        return parse(country, adm1, "", "");
    }

    /** country, island_country, adm1, region, subregion, bioregion ("-" where the specimen has none). */
    private static String place(Hashtable<String, Object> specimen) {
        StringBuilder out = new StringBuilder();
        for (String key : new String[] {"country", "island_country", "adm1", "region", "subregion", "bioregion"}) {
            out.append(out.length() == 0 ? "" : " | ").append(specimen.get(key) == null ? "-" : specimen.get(key));
        }
        return out.toString();
    }

    private String report() {
        MessageMgr messages = ((AntwebUpload) upload).getMessageMgr();
        messages.compileMessages(group);
        return messages.getMessagesReport();
    }

    static final String HAWAII = "Hawaii | Hawaii | - | Oceania | Polynesia | Oceania";
    static final String GALAPAGOS = "Galapagos Islands | Galapagos Islands | - | Americas | South America | Neotropical";

    public void testHawaiiInEveryFormAFileWrites() throws Exception {
        assertEquals(HAWAII, place(parse("United States", "Hawaii")));
        assertEquals(HAWAII, place(parse("Hawaii", "")));
        assertEquals("USA is respelled first", HAWAII, place(parse("united states", "Hawaii")));
        // Oahu (row 1017) is an old name of Hawaii: the record is Hawaii's and the adm1 goes
        assertEquals(HAWAII, place(parse("United States", "Oahu")));
        assertEquals(HAWAII, place(parse("Hawaii", "Oahu")));
        assertEquals(HAWAII, place(parse("Hawaii", "Hawaii")));
    }

    public void testGalapagosInEveryFormAFileWrites() throws Exception {
        assertEquals(GALAPAGOS, place(parse("Ecuador", "Galapagos")));
        assertEquals(GALAPAGOS, place(parse("Ecuador", "Galápagos")));
        assertEquals(GALAPAGOS, place(parse("Galapagos Islands", "")));
        assertEquals("an old name of the island as the country", GALAPAGOS, place(parse("Galapagos", "")));
        assertEquals(GALAPAGOS, place(parse("Galápagos", "")));
    }

    /** An adm1 the island has no row for is kept as written, and is not reported as a state AntWeb does not know. */
    public void testAnAdm1TheIslandHasNoRowForIsKept() throws Exception {
        assertEquals("Hawaii | Hawaii | Maui | Oceania | Polynesia | Oceania", place(parse("Hawaii", "Maui")));
        assertEquals("Galapagos Islands | Galapagos Islands | Isabela | Americas | South America | Neotropical", place(parse("Galapagos Islands", "Isabela")));
        // the control: a state AntWeb does not know in a country that is not such a place is reported, as before
        assertEquals("United States | - | Atlantis | Americas | North America | Nearctic", place(parse("United States", "Atlantis")));
        // "Not valid Antweb adm1" links each name to the adm1 manager (adm1Mgr.do?adm1Name=...)
        String report = report();
        assertTrue(report, report.contains("<b>Not valid Antweb adm1</b>") && report.contains("adm1Name=Atlantis&amp;countryName=United+States"));
        assertFalse(report, report.contains("adm1Name=Maui") || report.contains("adm1Name=Isabela"));
    }

    /** Mauritius + adm1 Rodrigues: the country Rodrigues (not flagged as an island: no island_country). */
    public void testRodriguesOfMauritius() throws Exception {
        assertEquals("Rodrigues | - | - | Africa | Eastern Africa | Malagasy", place(parse("Mauritius", "Rodrigues")));
        assertEquals("Rodrigues | - | - | Africa | Eastern Africa | Malagasy", place(parse("Rodrigues", "")));
        assertEquals("Mauritius | - | - | Africa | Eastern Africa | Malagasy", place(parse("Mauritius", "")));
    }

    public void testEveryOtherRecordIsStoredAsBefore() throws Exception {
        assertEquals("United States | - | California | Americas | North America | Nearctic", place(parse("United States", "California")));
        assertEquals("Ecuador | - | - | Americas | South America | Neotropical", place(parse("Ecuador", "")));
        assertEquals("Reunion | - | - | Africa | Eastern Africa | Malagasy", place(parse("Reunion", "")));
    }

    /**
     * The island row has no region or subregion (null, blank, or the text "none" or "null"): the record keeps those of
     * the country its file wrote, as the one-time update keeps the stored specimen's (new_value in
     * tools/place/island_countries.py). The text "none" is never stored. Before: the value was removed on null and
     * "none" was stored.
     */
    public void testAnIslandRowWithoutRegionKeepsTheRecordsOwn() throws Exception {
        Country hawaii = countries.get("Hawaii");
        for (String none : new String[] {null, "", "none", "null", " None "}) {
            hawaii.setRegion(none);
            hawaii.setSubregion(none);
            assertEquals("row value [" + none + "]", "Hawaii | Hawaii | - | Americas | North America | Oceania", place(parse("United States", "Hawaii")));
            assertEquals("row value [" + none + "]", "Hawaii | Hawaii | - | Americas | North America | Oceania", place(parse("United States", "Oahu")));
            // the file wrote the island itself: there is no other value, and none is stored
            assertEquals("row value [" + none + "]", "Hawaii | Hawaii | - | - | - | Oceania", place(parse("Hawaii", "")));
        }
        // one of the two
        hawaii.setRegion("Oceania");
        hawaii.setSubregion("none");
        assertEquals("Hawaii | Hawaii | - | Oceania | North America | Oceania", place(parse("United States", "Hawaii")));
    }

    /**
     * The coordinates of an island's record are checked against the island's own bounding box (before: the political
     * parent's). San Francisco lies in the box of the United States and outside Hawaii's.
     */
    public void testCoordinatesAreCheckedAgainstTheIslandsBox() throws Exception {
        Hashtable<String, Object> honolulu = parse("United States", "Hawaii", "21.3", "-157.86");
        assertEquals(HAWAII, place(honolulu));
        assertNull("inside Hawaii's box", honolulu.get("flag"));

        Hashtable<String, Object> wrong = parse("United States", "Hawaii", "37.77", "-122.42");
        assertEquals(HAWAII, place(wrong));
        assertEquals("red", wrong.get("flag"));
        assertEquals(MessageMgr.latLonNotInCountryBounds, wrong.get("issue"));

        Hashtable<String, Object> mainland = parse("United States", "California", "37.77", "-122.42");
        assertNull(mainland.get("flag"));

        // the Galapagos box as widened on 2026-11-11; a point on the mainland of Ecuador is outside it
        assertNull(parse("Ecuador", "Galapagos", "-0.74", "-90.3").get("flag"));
        assertEquals("red", parse("Ecuador", "Galapagos", "-1.677", "-79.645").get("flag"));
        assertNull(parse("Ecuador", "", "-1.677", "-79.645").get("flag"));
    }

    /** The caches are put back: another test in the same run sees what it would have seen. */
    public void testThePlacesInMemoryArePutBack() throws Exception {
        Object mine = GeolocaleMgr.getCountry("Hawaii");
        assertSame(countries.get("Hawaii"), mine);
        tearDown();
        assertNotSame(mine, GeolocaleMgr.getCountry("Hawaii"));
        setUp();
    }
}

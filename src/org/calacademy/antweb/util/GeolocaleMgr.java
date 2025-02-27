package org.calacademy.antweb.util;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Overview;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.GeolocaleDb;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeolocaleMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(GeolocaleMgr.class);

    // This is a deep copy. Used for menus.
    private static ArrayList<Region> s_regions;

    // Used for most of the getGeolocale() methods.
    private static ArrayList<Geolocale> s_geolocales;

    // For Taxon Name Search Autocomplete    
    private static List<String> placeNamesList;

    private static ArrayList<Adm1> adm1List;

    /** map of all country names, not just valid ones
     */
    private static Map<String, Country> countryNameMap;

    /**
     * key is pair of adm1Name, countryName, value is the Adm1 object
     */
    private static final MultiKeyMap<String, Adm1> adm1CountryMap = new MultiKeyMap<>();

    public static boolean isInitialized() {
        return s_regions != null;
    }

    private static boolean s_oneAtATime = false;

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) throws SQLException {
        if (!forceReload && s_regions != null) return;

        Date startTime = new Date();

        if (s_regions == null || forceReload) {
            if (!s_oneAtATime) {
                s_oneAtATime = true;
            } else {
                return;
            }

            // about 12 seconds. Maybe move into postInitialize? Probably needed.
            GeolocaleMgr.populateDeep(connection, true);
            GeolocaleMgr.populateShallow(connection, true);
            // For Place Name Search Autocomplete
            placeNamesList = new GeolocaleDb(connection).getPlaceNames();

            s_oneAtATime = false;
        }
        logDeep(s_regions);
    }

    //Called through UtilAction to, in a separate thread.
    public static void postInitialize(Connection connection) {
        //A.log("postInitialize BEFORE");
        GeolocaleDb.buildGetChildrenWithTaxonHash(connection);
        //A.log("postInitialize AFTER");  // This takes almost a 55 seconds on dev laptop.
    }

    private static void populateDeep(Connection connection, boolean forceReload) throws SQLException {
        if (!forceReload && s_regions != null) return;

        GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        // deep crawl through subregion, countries and adm1.  Use for Georegion menu.
        s_regions = geolocaleDb.getRegions(true);
    }

    private static void populateShallow(Connection connection, boolean forceReload) {

        if (!forceReload && s_geolocales != null) return;

        //A.log("populateShallow forceReload:" + forceReload + " s_geolocales:" + s_geolocales);

        s_geolocales = new ArrayList<>();
        for (Region region : s_regions) {
            s_geolocales.add(region);
            for (Subregion subregion : region.getSubregions()) {
                s_geolocales.add(subregion);
                for (Country country : subregion.getAllCountries()) {
                    s_geolocales.add(country);
                    if (country.isIsland()) islands.add(country);
                    //A.log("populateShallow() country:" + country);
                    //if ("Albania".equals(country.toString())) A.log("populateShallow() country:" + country + " id:" + country.getId());
                    //if (adm1.getParent().equals("Venezuela")) A.log("populateShallow() country:" + country + " id:" + adm1.getId() + " adm1:" + adm1.getName());
                    s_geolocales.addAll(country.getAllAdm1s());
                }
            }
        }

        GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        //A.log("populateShallow() 1 c:" + countInstances("Albania", s_geolocales));

        // Get all of the countries that don't have parents set, as we won't find them in the above process.
        s_geolocales.addAll(geolocaleDb.getGeolocales("country", "none", false));
        //A.log("populateShallow() 2 c:" + countInstances("Albania", s_geolocales));

        adm1List = s_geolocales.stream()
                .filter(Geolocale::isAdm1)
                .map(adm1 -> (Adm1) adm1)
                .collect(Collectors.toCollection(ArrayList::new));


        adm1List.forEach(adm1 -> adm1CountryMap.put(adm1.getName(), adm1.getCountry(), adm1));

        countryNameMap = s_geolocales.stream().filter(Geolocale::isCountry)
            .map(country -> (Country) country).collect(Collectors.toMap(Country::getName, Function.identity()));

        Collections.sort(s_geolocales);
    }

    private static int countInstances(String instance, ArrayList<Geolocale> geolocales) {
        if (instance == null) return 0;
        int c = 0;
        for (Geolocale g : geolocales) {
            if (instance.equals(g.toString())) {
                ++c;
            }
        }
        return c;
    }

    private static void logDeep(ArrayList<Region> regions) {
        int geolocaleCount = 0;
        for (Region region : regions) {
            ++geolocaleCount;
            //A.log("region:" + region + " size:" + region.getSubregions().size());
            for (Subregion subregion : region.getSubregions()) {
                ++geolocaleCount;
                //A.log("  subregion:" + subregion + " size:" + subregion.getCountries().size());
                for (Country country : subregion.getAllCountries()) {
                    ++geolocaleCount;
                    //if ("Venezuela".equals(country.getName())) A.log("    country:" + country + " size:" + country.getAllAdm1s().size());
                    for (Adm1 adm1 : country.getAllAdm1s()) {
                        ++geolocaleCount;
                        //if ("Venezuela".equals(adm1.getParent())) A.log("      adm1:" + adm1);
                    }
                }
            }
        }
        //A.log("logDeep() geolocaleCount:" + geolocaleCount);
    }

    // For Taxon Name Search Autocomplete
    public static List<String> getPlaceNames(String text) {
        if (placeNamesList == null) {
            s_log.debug("GeolocaleMgr.getPlaceNames(text) initializing...");
            return null;
        }
        if (text == null) {
            s_log.warn("GeolocaleMgr.getPlaceNames(text) text is null");
            return null;
        }
        text = text.toLowerCase();
        //A.log("TaxonMgr.getPrettyTaxaNames(text) text:" + text + " prettyTaxaListSize:" + prettyTaxaNamesList.size());
        String[] texts = text.split(" ");
        List<String> placeNamesSubset = new ArrayList<>();
        int i = 0;
        for (String placeName : placeNamesList) {
            boolean containsAll = true;
            for (String s : texts) {
                //log("getPlaceNames() text:" + text + " j:" + texts[j] + " placeName:" + placeName);
                if (!placeName.toLowerCase().contains(s)) {
                    containsAll = false;
                    break;
                }
            }
            if (containsAll) {
                placeNamesSubset.add(placeName);
                ++i;
                if (i > 6000) break; // Greater than 8044 - the number of valid adm1 (5516)
            }
        }
        //A.log("TaxonMgr.getPrettyTaxaNames(q) returning size:" + prettyTaxaNamesSubset.size());
        return placeNamesSubset;
    }


    public static ArrayList<Geolocale> getValidChildren(String parent, String georank) {
        //A.log("getValidChildren()");
        for (Region region : GeolocaleMgr.getDeepRegions()) {
            //A.log("getValidChildren() region:" + region + " size:" + region.getSubregions().size());
            if (Georank.region.equals(georank)) {
                if (region.getName().equals(parent)) {
                    return region.getValidChildren(); //region.getSubregions();
                }
            } else {
                //A.log("region:" + region + " size:" + region.getSubregions().size());
                for (Subregion subregion : region.getSubregions()) {
                    if (Georank.subregion.equals(georank)) {
                        if (subregion.getName().equals(parent)) {
                            return subregion.getValidChildren(); //.getCountries();
                        }
                    } else {
                        //A.log("  subregion:" + subregion + " size:" + subregion.getCountries().size());
                        for (Country country : subregion.getAllCountries()) {  // Do not excludes ones not live.
                            //if ("South America".equals(subregion.getName())) A.log("    country:" + country + " size:" + subregion.getCountries().size() + " georank:" + georank  + " parent:" + parent);
                            if (Georank.country.equals(georank)) {
                                if (country.getName().equals(parent)) {
                                    return country.getValidChildren(); //.getAdm1s();
                                }
                            } else {
                                return null;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // Deep copy.
    public static ArrayList<Region> getDeepRegions() {
        return s_regions;
    }


    // These need to be removed/changed. Affecting Species List Tool. Use Ids there.
    // Convenience method
    public static int getGeolocaleId(String name) {
        Geolocale geolocale = getGeolocale(name);
        if (geolocale == null) return 0;
        return geolocale.getId();
    }

    public static @Nullable Geolocale getGeolocale(String name) {
        if (!AntwebMgr.isPopulated()) return null;
//      try {
        if (Utility.isNumber(name)) {
            int i = Integer.parseInt(name);
            //A.log("GeolocaleMgr.getGeolocale(String) i:" + i + " geo:" + geolocale);
            return getGeolocale(i);
        }

        Geolocale geolocale = getRegion(name);
        if (geolocale != null) return geolocale;

        geolocale = getSubregion(name);
        if (geolocale != null) return geolocale;

        geolocale = getCountry(name);
        if (geolocale != null) return geolocale;

        geolocale = getAdm1(name);
        //A.log("getGeolocale(name) BEING USED for adm1. Searching for Adm1 by name not allowed. Stop it. name:" + name);
        // This can be used in non-critical ways. For instance, Place Names search box. Will almost always be right.
        return geolocale;
    }

    // To be removed... because not unique.
    private static Geolocale getAdm1(String adm1Name) {
        for (Geolocale geolocale : s_geolocales) {
            if ("adm1".equals(geolocale.getGeorank()) && geolocale.getName().equals(adm1Name)) return geolocale;
        }
        return null;
    }

    public static Geolocale getAdm1IfUnique(String adm1Name) {
        Geolocale adm1 = null;
        for (Geolocale geolocale : s_geolocales) {
            if ("adm1".equals(geolocale.getGeorank()) && geolocale.getName().equals(adm1Name)) {
                if (adm1 == null) {
                    adm1 = geolocale;
                } else {
                    return null;  // It was not unique
                }
            }
        }
        return adm1;
    }

    public static @Nullable Adm1 getAdm1(int geolocaleId) {
        return adm1List.stream().filter(geolocale -> geolocale.getId() == geolocaleId)
                .findFirst().orElse(null);
    }

    public static @Nullable Geolocale getGeolocale(int geolocaleId) {
        return s_geolocales.stream().filter(geolocale -> geolocale.getId() == geolocaleId)
                .findFirst().orElse(null);
    }

    public static @Nullable Geolocale getGeolocale(String name, String georank) {

        if ("adm1".equals(georank)) {
            s_log.warn("getGeolocale(name, georank) Illegal adm1 need parent to be unique");
            // We should return her
        }

        if (!"region".equals(georank) && !"subregion".equals(georank) && !"country".equals(georank)) {
            s_log.warn("getGeolocale() invalid call. Adm1 must include country.");
            AntwebUtil.logShortStackTrace();
        }

        if (name == null) {
            //s_log.error("getGeolocale() georank:" + georank + ", name is null, ");
            return null;
        }

        if (!AntwebMgr.isPopulated()) return null;

        for (Geolocale geolocale : s_geolocales) {
            if (georank.equals(geolocale.getGeorank()) && name.equals(geolocale.getName())) {
                //A.log("getGeolocale() name:" + name + " georank:" + georank + " found:" + geolocale);
                return geolocale;
            }
        }
        //A.log("getGeolocale() name:" + name + " georank:" + georank + " not found.");
        return null;
    }

    public static ArrayList<Geolocale> getGeolocales() {
        return s_geolocales;
    }

    public static @Nullable ArrayList<Geolocale> getLiveGeolocales() {
        if (s_geolocales == null) return null; // Could happen due to server initialization.

        return s_geolocales.stream().filter(Geolocale::getIsLive).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Get valid geolocales with a specific rank
     *
     * @param georank The rank to search for.
     * @return An arraylist of valid geolocales with the specified rank
     */
    public static @Nullable ArrayList<Geolocale> getValidGeolocales(@NotNull String georank) {
        // Ensure that antweb has loaded data
        AntwebMgr.isPopulated();

        if (s_geolocales == null) return null; // Could happen due to server initialization.

        return s_geolocales.stream()
                .filter(Geolocale::getIsValid)
                .filter(geolocale -> geolocale.getGeorank().equals(georank))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static @Nullable ArrayList<Geolocale> getGeolocales(String georank) {
        return GeolocaleMgr.getGeolocales(georank, false);
    }

    /**
     * Get all geolocales that match one or more conditions
     *
     * @param georank filter geolocales by georank. If null, get all georanks
     * @param onlyValid true if results should include only valid geolocales, false if invalid geolocales should be included
     * @return An ArrayList of geolocales that match the parameters
     */
    public static @Nullable ArrayList<Geolocale> getGeolocales(@Nullable String georank, boolean onlyValid) {

        AntwebMgr.isPopulated();

        if (s_geolocales == null) return null; // Could happen due to server initialization.

        Stream<Geolocale> geolocaleStream = s_geolocales.stream();

        if (onlyValid) {
            geolocaleStream = geolocaleStream.filter(Geolocale::getIsValid);
        }

        // Filter by georank, or get all if null
        if (georank != null) {
            geolocaleStream = geolocaleStream.filter(geolocale -> geolocale.getGeorank().equals(georank));
        }

        return geolocaleStream.collect(Collectors.toCollection(ArrayList::new));
    }

    // Convenience methods:
    public static ArrayList<Geolocale> getAllCountries() {
        return GeolocaleMgr.getGeolocales("country");
    }

    public static @Nullable ArrayList<Geolocale> getValidCountries() {
        ArrayList<Geolocale> validCountries = GeolocaleMgr.getValidGeolocales("country");
        Collections.sort(validCountries);
        return validCountries;
    }

    public static @NotNull ArrayList<Geolocale> getValidAdm1s() {
        return adm1List.stream().filter(Adm1::isValid).collect(Collectors.toCollection(ArrayList::new));
    }

    public static ArrayList<Geolocale> getAllAdm1s() {
        return new ArrayList<>(adm1List);
    }

    // Return deep copies
    public static Geolocale getDeepGeolocale(String name, String georank) {
        if (Geolocale.ADM1.equals(georank)) {
            s_log.warn("getDeepGeolocale() fix. Adm1 not supported.");
            AntwebUtil.logShortStackTrace();
        }
        if (Geolocale.REGION.equals(georank)) return GeolocaleMgr.getRegion(name);
        if (Geolocale.SUBREGION.equals(georank)) return GeolocaleMgr.getSubregion(name);
        if (Geolocale.COUNTRY.equals(georank)) return GeolocaleMgr.getCountry(name);
        // if (Geolocale.ADM1.equals(georank)) return GeolocaleMgr.getAdm1(name);  // Unsupported.
        return null;
    }

    public static Geolocale getDeepGeolocale(int id) {
        Geolocale geolocale = getGeolocale(id);
        if (geolocale == null) return null;
        return getDeepGeolocale(geolocale.getName(), geolocale.getGeorank());
    }

    public static Region getRegion(String name) {
        if (s_regions == null || name == null) return null;
        for (Region region : s_regions) {
            if (name.equals(region.getName())) return region;
        }
        return null;
    }

    // To Be deprecated. Replace with getDeepRegions()
    public static ArrayList<Geolocale> getRegions() {
        return GeolocaleMgr.getGeolocales("region");
    }

    public static ArrayList<Geolocale> getSubregions() {
        return GeolocaleMgr.getGeolocales("subregion");
    }

    public static Subregion getSubregion(String name) {
        if (name == null) return null;
        if (s_regions == null) return null;
        for (Region region : s_regions) {
            for (Subregion subregion : region.getSubregions()) {
                if (name.equals(subregion.getName())) return subregion;
            }
        }
        return null;
    }

    public static @Nullable Country getCountry(String name) {
        if (name == null) return null;
        if (s_regions == null) return null;

        /*
        // Queensland is not in list of countries.
        if (AntwebProps.isDevMode() && "Queensland".equals(name)) {
            A.log("name:" + name + " size:" + countryNameMap.size() + " mapped:" + countryNameMap.get(name));
            for (String cName : countryNameMap.keySet()) {
                A.log("getCountry() name:" + cName);
            }
        }
        */

        return countryNameMap.get(name);
    }

    /*
    public static Country getLiveCountry(String name) {
      if (name == null) return null;
      if (s_regions == null) return null;
      for (Region region : s_regions) {
        if (region.getIsLive()) 
        for (Subregion subregion : region.getSubregions()) {
          if (subregion.getIsLive())
          for (Country country : subregion.getAllCountries()) {
            if (country.getIsLive())
            if (name.equals(country.getName())) return country;
          }
        }
      }
      return null;
    }
*/
    public static Country getCountryWithLowerCaseNoSpace(String name) {
        if (s_regions == null) return null;
        for (Region region : s_regions) {
            for (Subregion subregion : region.getSubregions()) {
                for (Country country : subregion.getAllCountries()) {
                    String countryName = country.getName().toLowerCase();
                    countryName = new Formatter().removeSpaces(countryName);
                    if (name.equals(countryName)) return country;
                }
            }
        }
        return null;
    }

    public static ArrayList<Geolocale> getCountries() {
        return GeolocaleMgr.getGeolocales("country");
    }
    // if (!AntwebMgr.isPopulated()) return null;

    public static @Nullable Geolocale getAnyCountry(String countryName) {
        if (countryName == null) return null;
        ArrayList<Geolocale> countries = getCountries();
        if (countries == null) return null; // Could happen due to server initialization.
        for (Geolocale geolocale : getCountries()) {
            if (countryName.equals(geolocale.getName())) return geolocale;
        }
        return null;
    }


    /**
     * A country name may be for an invalid or non-UN country.  The (Antweb) valid country will be returned.
     * I.E: "Iran (Islamic Republic of)" will return Iran.
     */
    public static @Nullable Country getValidCountry(String country) {
        if (countryNameMap == null) {
            return null;
        }
        Country matchingCountry = countryNameMap.get(country);

        if (matchingCountry == null) {
            return null;
        }

        if (matchingCountry.isValid()) {
            return matchingCountry;
        }

        // country is not valid, return the Country that this points to
        return countryNameMap.get(matchingCountry.getValidName());
    }


    // Convenience method
    public static int getCountryId(String name) {
        Geolocale country = getCountry(name);
        if (country == null) return 0;
        return country.getId();
    }

    public static ArrayList<String> getValidCountryList() {
        // Used by generic list.do and ListAction.java.  Better to use objects (getValidCountries()).
        ArrayList<String> validCountryList = new ArrayList<>();

        ArrayList<Geolocale> validCountries = GeolocaleMgr.getGeolocales("country", true);
        for (Geolocale country : validCountries) {
            validCountryList.add(country.getName());
        }
        return validCountryList;
    }

    public static boolean isValid(String adm1Name, String countryName) {
        Geolocale adm1 = GeolocaleMgr.getAdm1(adm1Name, countryName);
        if (adm1 != null) {
            return adm1.getIsValid();
        }
        return false;
    }

    public static boolean isValid(String countryName) {
        Geolocale country = GeolocaleMgr.getCountry(countryName);
        if (country != null) {
            return country.getIsValid();
        }
        return false;
    }
    
    
    
/*
298 getCountries
250 getValidCountries

    public static boolean isValid(String name) {
      ArrayList<Geolocale> geolocales = getGeolocales();
      for (Geolocale geolocale : geolocales) {
A.log("isValid() " + name + " = " + geolocale.getName() + "?");
        if (geolocale.getName().equals(name)) {
          return true;
        }
      }      
      return false; 
    }
*/

    public static boolean isValidCountry(String name) {
        Country country = countryNameMap.get(name);
        if (country == null) { return false; }
        return country.isValid();
    }

    public static boolean isValidAdm1(String name) {
        ArrayList<Geolocale> validAdm1s = getValidAdm1s();
        for (Geolocale geolocale : validAdm1s) {
            if (geolocale.getName().equals(name))
                return true;
        }
        return false;
    }

    public static ArrayList<Geolocale> getAdm1s() {
        return new ArrayList<>(adm1List);
    }

    public static @NotNull ArrayList<Geolocale> getAdm1sWithSpecimen() {
        return adm1List.stream()
                .filter(adm1 -> adm1.getSpecimenCount() > 0)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static @Nullable Geolocale inferCountry(String adm1Name) {
        // Will only return if unique
        if (adm1List == null) return null; // could be server initializing

        if (adm1Name == null) return null;

        Geolocale adm1 = null;
        for (Geolocale geolocale : adm1List) {
            if (adm1Name.equals(geolocale.getName())) {
                if (adm1 != null) return null; // Didn't find a unique adm1.
                adm1 = geolocale;
            }
        }
        if (adm1 != null) {
            return getValidCountry(adm1.getParent());
        }
        return null;
    }

    public static @Nullable Geolocale getAnyAdm1(String adm1Name, String countryName) {
        if (!AntwebMgr.isPopulated()) return null;
        if (adm1Name == null) return null;
        if (countryName == null) {
            Geolocale country = inferCountry(adm1Name);
            if (country == null) {
                //s_log.info("getAnyAdm1(" + adm1Name + ", " + countryName + ") must included countryName.");
                return null;
            } else countryName = country.getName();
        }
        return adm1CountryMap.get(adm1Name, countryName);
    }

    /** Only from valid countries!
     * @param adm1Name The adm1 to search for
     * @param countryName The country the Adm1 is in. Will be converted to valid country if invalid
     * @return The matching Adm1 from the valid country or null if not found
     */
    public static @Nullable Geolocale getAdm1(String adm1Name, String countryName) {
        Country country = GeolocaleMgr.getValidCountry(countryName);
        if (country == null) return null; // Could be server initializing.

        Adm1 matching_adm1 = adm1CountryMap.get(adm1Name, country.getName());

        if (matching_adm1 != null && matching_adm1.isValid()) {
            return matching_adm1;
        }
        return null;

    }

    /**
     * Will return the adm1, or the validName adm1 if the found adm1 is not valid.
     * <p>
     * todo document what conditions will make this return null
     * <p>
     * todo should we match adm1's parent against getValidCountry?
     *
     * @param adm1    The name of the adm1 to search for
     * @param country The country name to match from. Must exactly match the adm1's getParent()
     * @return The valid adm1 or null if not found.
     */
    public static @Nullable Geolocale getValidAdm1(String adm1, String country) {

        Adm1 matching_adm1 = adm1CountryMap.get(adm1, country);

        if (matching_adm1 == null) {
            return null;
        }

        if (matching_adm1.isValid()) {return matching_adm1;}

        Adm1 valid_adm1 = adm1CountryMap.get(matching_adm1.getValidName(), country);

        if (valid_adm1 == null) {
            // This will show up in the upload report.
            //s_log.warn("getValidAdm1 " + adm1 + " with valid name: " + matching_adm1.getValidName() + " with country " + country + " not found in adm1CountryMap");
            return null;
        }

        if (valid_adm1.isValid()) {     // todo is this really necessary? are there any adm1's whose validName isn't valid?
            return valid_adm1;
        }
        return null;
    }

    public static String getRegionsDisplay() {
        String newLine = "\r\n";
        String indent = "  ";
        StringBuilder display = new StringBuilder();
        for (Region region : s_regions) {
            display.append(newLine).append(region);
            for (Subregion subregion : region.getSubregions()) {
                display.append(newLine).append(indent).append(subregion);
                for (Country country : subregion.getLiveCountries()) {
                    display.append(newLine).append(indent).append(indent).append(country);
                    for (Adm1 adm1 : country.getAllAdm1s()) {
                        display.append(newLine).append(indent).append(indent).append(indent).append(adm1);
                    }
                }
            }
        }
        return display.toString();
    }

    public static String getGeoregionsDisplayHtml() {
        String newLine = "<br>";
        String indent = "&nbsp;&nbsp;&nbsp;&nbsp;";
        StringBuilder display = new StringBuilder("<h3>Georegions</h3><br>");
        for (Region region : s_regions) {
            display.append(newLine).append(region.getTag());
            for (Subregion subregion : region.getSubregions()) {
                display.append(newLine).append(indent).append(subregion.getTag());
                for (Country country : subregion.getLiveCountries()) {
                    if (country.getIsValid()) {
                        display.append(newLine).append(indent).append(indent).append(country.getTag());
                        display.append(" - ").append(country.getBioregion()).append(" + ").append(country.getSpecimenCount());
                    }
                    for (Adm1 adm1 : country.getValidAdm1s()) {
                        display.append(newLine).append(indent).append(indent).append(indent).append(adm1.getTag());
                    }
                }
            }
        }
        return display.toString();
    }

    public static String getGeolocaleBioregion(String countryName, String adm1Name) {
        String useBioregion = null;

        Geolocale adm1 = getAdm1(adm1Name, countryName);
        if (adm1 != null) {
            useBioregion = adm1.getTheOneBioregion();
        }
        if (useBioregion == null) {
            Geolocale country = getCountry(countryName);
            if (country != null) {
                useBioregion = country.getTheOneBioregion();
            }
        }
        //A.log("GeolocaleMgr.getGeolocaleBioregion() countryName:" + countryName + " adm1Name:" + adm1Name + " useBioregion:" + useBioregion);
        return useBioregion;
    }


    public static String getAdm1CountryData() {
        StringBuilder adm1CountryData = new StringBuilder();

        ArrayList<Geolocale> geolocales = GeolocaleMgr.getGeolocales();
        ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

        for (Region region : regions) {

            for (Subregion subregion : region.getSubregions()) {

                for (Country country : subregion.getAllCountries()) {
                    if (!country.isValid()) continue;
                    for (Adm1 adm1 : country.getAllAdm1s()) {
                        if (adm1.getIsValid()) {
                            String line = adm1 + "\t" + country + "\r";
                            adm1CountryData.append(line);
                        }
                    }
                }
            }
        }
        return adm1CountryData.toString();
    }


    public static String getAcceptedAdm1CountryData() {
        StringBuilder adm1CountryData = new StringBuilder();

        ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

        for (Region region : regions) {

            for (Subregion subregion : region.getSubregions()) {

                for (Country country : subregion.getAllCountries()) {
                    if (!country.isValid()) continue;
                    for (Adm1 adm1 : country.getAllAdm1s()) {
                        if (!adm1.isValid() && adm1.getValidName() == null) continue;
                        String line = adm1 + "\t" + country + "\r";
                        adm1CountryData.append(line);
                    }
                }
            }
        }
        return adm1CountryData.toString();
    }

    public static boolean isAccepted(Adm1 candidate) {
        ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

        for (Region region : regions) {

            for (Subregion subregion : region.getSubregions()) {

                for (Country country : subregion.getAllCountries()) {
                    if (!country.isValid()) continue;
                    for (Adm1 adm1 : country.getAllAdm1s()) {
                        if (!adm1.isValid() && adm1.getValidName() == null) continue;
                        if (candidate.getId() == adm1.getId()) return true;
                    }
                }
            }
        }
        return false;
    }

    /*
         Because Hawaii is in a separate bioregion from United States we treat it as a country
        (stored in Geolocale table with georank = "country".
    */

    private static final ArrayList<Country> islands = new ArrayList<>();

    public static ArrayList<Country> getIslands() {
        return islands;
    }

    public static Country getIsland(String element) {
        for (Country island : islands) {
            if (island.getName().equals(element)) {
                return island;
            }
        }
        return null;
    }

    public static boolean isIsland(String name) {
        return getIsland(name) != null;
    }

    public static Country getValidIsland(String name) {
        Country validIsland = null;
        Country island = getIsland(name);
        if (island != null) {
            if (island.isValid()) {
                validIsland = island;
            } else {
                validIsland = getIsland(island.getValidName());
            }
        }
        //A.log("getValidIsland() name:" + name + " island:" + validIsland);
        return validIsland;
    }


    public static Overview getGeolocaleFromProjectName(String projectName) {

        // United States Adm1
        if ("alabamaants".equals(projectName)) return GeolocaleMgr.getAdm1("Alabama", "United States");
        if ("alaskaants".equals(projectName)) return GeolocaleMgr.getAdm1("Alaska", "United States");
        if ("arizonaants".equals(projectName)) return GeolocaleMgr.getAdm1("Arizona", "United States");
        if ("arkansasants".equals(projectName)) return GeolocaleMgr.getAdm1("Arkansas", "United States");
        if ("californiaants".equals(projectName)) return GeolocaleMgr.getAdm1("California", "United States");
        if ("coloradoants".equals(projectName)) return GeolocaleMgr.getAdm1("Colorado", "United States");
        if ("connecticutants".equals(projectName)) return GeolocaleMgr.getAdm1("Connecticut", "United States");
        if ("delawareants".equals(projectName)) return GeolocaleMgr.getAdm1("Delaware", "United States");
        if ("floridaants".equals(projectName)) return GeolocaleMgr.getAdm1("Florida", "United States");
        if ("georgiaants".equals(projectName)) return GeolocaleMgr.getAdm1("Georgia", "United States");
        if ("idahoants".equals(projectName)) return GeolocaleMgr.getAdm1("Idaho", "United States");
        if ("illinoisants".equals(projectName)) return GeolocaleMgr.getAdm1("Illinois", "United States");
        if ("indianaants".equals(projectName)) return GeolocaleMgr.getAdm1("Indiana", "United States");
        if ("iowaants".equals(projectName)) return GeolocaleMgr.getAdm1("Iowa", "United States");
        if ("kansasants".equals(projectName)) return GeolocaleMgr.getAdm1("Kansas", "United States");
        if ("kentuckyants".equals(projectName)) return GeolocaleMgr.getAdm1("Kentucky", "United States");
        if ("louisianaants".equals(projectName)) return GeolocaleMgr.getAdm1("Louisiana", "United States");
        if ("maineants".equals(projectName)) return GeolocaleMgr.getAdm1("Maine", "United States");
        if ("marylandants".equals(projectName)) return GeolocaleMgr.getAdm1("Maryland", "United States");
        if ("massachusettsants".equals(projectName)) return GeolocaleMgr.getAdm1("Massachusetts", "United States");
        if ("michiganants".equals(projectName)) return GeolocaleMgr.getAdm1("Michigan", "United States");
        if ("minnesotaants".equals(projectName)) return GeolocaleMgr.getAdm1("Minnesota", "United States");
        if ("mississippiants".equals(projectName)) return GeolocaleMgr.getAdm1("Mississippi", "United States");
        if ("missouriants".equals(projectName)) return GeolocaleMgr.getAdm1("Missouri", "United States");
        if ("montanaants".equals(projectName)) return GeolocaleMgr.getAdm1("Montana", "United States");
        if ("nebraskaants".equals(projectName)) return GeolocaleMgr.getAdm1("Nebraska", "United States");
        if ("nevadaants".equals(projectName)) return GeolocaleMgr.getAdm1("Nevada", "United States");
        if ("newhampshireants".equals(projectName)) return GeolocaleMgr.getAdm1("New Hampshire", "United States");
        if ("newjerseyants".equals(projectName)) return GeolocaleMgr.getAdm1("New Jersey", "United States");
        if ("newmexicoants".equals(projectName)) return GeolocaleMgr.getAdm1("New Mexico", "United States");
        if ("newyorkants".equals(projectName)) return GeolocaleMgr.getAdm1("New York", "United States");
        if ("northcarolinaants".equals(projectName)) return GeolocaleMgr.getAdm1("North Carolina", "United States");
        if ("northdakotaantsants".equals(projectName)) return GeolocaleMgr.getAdm1("North Dakota", "United States");
        if ("ohioants".equals(projectName)) return GeolocaleMgr.getAdm1("Ohio", "United States");
        if ("oklahomaants".equals(projectName)) return GeolocaleMgr.getAdm1("Oklahoma", "United States");
        if ("oregonants".equals(projectName)) return GeolocaleMgr.getAdm1("Oregon", "United States");
        if ("pennsylvaniaants".equals(projectName)) return GeolocaleMgr.getAdm1("Pennsylvania", "United States");
        if ("rhodeislandants".equals(projectName)) return GeolocaleMgr.getAdm1("Rhode Island", "United States");
        if ("southcarolinaants".equals(projectName)) return GeolocaleMgr.getAdm1("South Carolina", "United States");
        if ("southdakotaants".equals(projectName)) return GeolocaleMgr.getAdm1("South Dakota", "United States");
        if ("tennesseeants".equals(projectName)) return GeolocaleMgr.getAdm1("Tennessee", "United States");
        if ("texasants".equals(projectName)) return GeolocaleMgr.getAdm1("Texas", "United States");
        if ("utahants".equals(projectName)) return GeolocaleMgr.getAdm1("Utah", "United States");
        if ("vermontants".equals(projectName)) return GeolocaleMgr.getAdm1("Vermont", "United States");
        if ("virginiaants".equals(projectName)) return GeolocaleMgr.getAdm1("Virginia", "United States");
        if ("washingtonants".equals(projectName)) return GeolocaleMgr.getAdm1("Washington", "United States");
        if ("washingtondcants".equals(projectName)) return GeolocaleMgr.getAdm1("Washington, D.C.", "United States");
        if ("westvirginiaants".equals(projectName)) return GeolocaleMgr.getAdm1("West Virginia", "United States");
        if ("wisconsinants".equals(projectName)) return GeolocaleMgr.getAdm1("Wisconsin", "United States");
        if ("wyomingants".equals(projectName)) return GeolocaleMgr.getAdm1("Wyoming", "United States");

        // Other country adm1s
        if ("newsouthwalesants".equals(projectName)) return GeolocaleMgr.getAdm1("New South Wales", "Australia");
        if ("southaustrailiaants".equals(projectName)) return GeolocaleMgr.getAdm1("South Australia", "Australia");
        if ("westernaustraliaants".equals(projectName)) return GeolocaleMgr.getAdm1("Western Australia", "Australia");
        if ("queenslandants".equals(projectName)) return GeolocaleMgr.getAdm1("Queensland", "Australia");
        if ("victoriaants".equals(projectName)) return GeolocaleMgr.getAdm1("Victoria", "Australia");
        if ("northernterritoryants".equals(projectName)) return GeolocaleMgr.getAdm1("Northern Territory", "Australia");
        if ("tasmaniaants".equals(projectName)) return GeolocaleMgr.getAdm1("Tasmania", "Australia");
        if ("australiancapitalterritoryants".equals(projectName)) return GeolocaleMgr.getAdm1("Australian Capital Territory", "Australia");
        if ("quebecants".equals(projectName)) return GeolocaleMgr.getAdm1("Quebec", "Canada");
        if ("albertaants".equals(projectName)) return GeolocaleMgr.getAdm1("Alberta", "Canada");
        if ("britishcolumbiaants".equals(projectName)) return GeolocaleMgr.getAdm1("British Columbia", "Canada");
        if ("novascotiaants".equals(projectName)) return GeolocaleMgr.getAdm1("Nova Scotia", "Canada");
        if ("ontarioants".equals(projectName)) return GeolocaleMgr.getAdm1("Ontario", "Canada");
        if ("newbrunswickants".equals(projectName)) return GeolocaleMgr.getAdm1("New Brunswick", "Canada");
        if ("saskatchewanants".equals(projectName)) return GeolocaleMgr.getAdm1("Saskatchewan", "Canada");
        if ("manitobaants".equals(projectName)) return GeolocaleMgr.getAdm1("Manitoba", "Canada");
        if ("newfoundlandandlabradorants".equals(projectName)) return GeolocaleMgr.getAdm1("Newfoundland and Labrador", "Canada");
        if ("northwestterritoriesants".equals(projectName)) return GeolocaleMgr.getAdm1("Northwest Territories", "Canada");
        if ("nunavutants".equals(projectName)) return GeolocaleMgr.getAdm1("Nunavut", "Canada");
        if ("princeedwardislandants".equals(projectName)) return GeolocaleMgr.getAdm1("Prince Edward Island", "Canada");
        if ("yukonants".equals(projectName)) return GeolocaleMgr.getAdm1("Yukon", "Canada");

/*  Species lists found on live server, in antweb container, here: /mnt/antweb/web/workingdir
      The country species lists were:
    mexicoants_speciesList.txt
    serbiaants_speciesList.txt
    chinaants_speciesList.txt
    micronesiaants_speciesList.txt
    seychellesants_speciesList.txt
    albertaants_speciesList.txt
    greeceants_speciesList.txt
    sloveniaants_speciesList.txt
    alicanteants_speciesList.txt
    comorosants_speciesList.txt
    solomonsants_speciesList.txt
    costaricaants_speciesList.txt
    netherlandsants_speciesList.txt
    creteants_speciesList.txt
    indiaants_speciesList.txt
    newguineaants_speciesList.txt
    croatiaants_speciesList.txt
    czechants_speciesList.txt
    iranants_speciesList.txt
    newzealandants_speciesList.txt
    ecuadorants_speciesList.txt
    italyants_speciesList.txt
    kenyaants_speciesList.txt
    westernaustraliaants_speciesList.txt
    austriaants_speciesList.txt
    fijiants_speciesList.txt
    paraguayants_speciesList.txt
    macaronesiaants_speciesList.txt
    barrowants_speciesList.txt
    madants_speciesList.txt
    philippinesants_speciesList.txt
    tanzaniaants_speciesList.txt
    xishuangbannaants_speciesList.txt
    malagasyants_speciesList.txt
    belgiumants_speciesList.txt
    martiniqueants_speciesList.txt
    queenslandants_speciesList.txt
    borneoants_speciesList.txt
    franceants_speciesList.txt
    matogrossodosulants_speciesList.txt
    tokelauants_speciesList.txt
    britishcolumbiaants_speciesList.txt
    frenchpolynesiaants_speciesList.txt
    mauritiusants_speciesList.txt
    reunionants_speciesList.txt
    uaeants_speciesList.txt
    galapagosants_speciesList.txt
    mayotteants_speciesList.txt
    saudiants_speciesList.txt
*/


        // ... incomplete list

        // countries
        String maybeCountry = Formatter.initCap(projectName);
        if (maybeCountry == null) return null;
        int i = maybeCountry.indexOf("ants");
        if (i > 0) maybeCountry = maybeCountry.substring(0, i);
        Country country = GeolocaleMgr.getCountry(maybeCountry);
        //A.log("maybeCountry:" + maybeCountry + " country:" + country);
        if (country != null) return country;

        // Countries that have spaces in names.
        if ("matogrossodosulants".equals(projectName)) return GeolocaleMgr.getCountry("Mato Grosso do Sul");
        if ("matogrossoants".equals(projectName)) return GeolocaleMgr.getCountry("Mato Grosso");
        if ("costaricaants".equals(projectName)) return GeolocaleMgr.getCountry("Costa Rica");

        // Why are these showing up in logs as unfound?
        if ("creteants".equals(projectName)) return GeolocaleMgr.getCountry("Crete");
        if ("alicanteants".equals(projectName)) return GeolocaleMgr.getCountry("Alicante");

        return null;
    }

}


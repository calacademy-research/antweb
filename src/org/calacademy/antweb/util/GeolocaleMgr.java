package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeolocaleMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(GeolocaleMgr.class);

    // This is a deep copy. Used for menus.
    private static ArrayList<Region> s_regions = null;

    // Used for most of the getGeolocale() methods.
    private static ArrayList<Geolocale> s_geolocales = null;

    /**
     * key is pair of adm1Name, countryName, value is the Adm1 object
     */
    private static final MultiKeyMap<String, Adm1> s_adm1_map = new MultiKeyMap<>();


    private static ArrayList<Adm1> s_adm1s;

    private static Map<String, Country> s_country_map;

    // For Taxon Name Search Autocomplete    
    private static List<String> placeNamesList = null;

    public static boolean isInitialized() {
        return s_regions != null;
    }

    private static boolean s_oneAtATime = false;

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) {
        if (!forceReload && (s_regions != null)) return;

        java.util.Date startTime = new java.util.Date();

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
            placeNamesList = (new GeolocaleDb(connection)).getPlaceNames();

            s_oneAtATime = false;
        }
        logDeep(s_regions);
    }

    //Called through UtilAction to, in a separate thread, populate the curators with adm1.
    public static void postInitialize(Connection connection) throws SQLException {
        //
    }

    private static void populateDeep(Connection connection, boolean forceReload) {
        if (!forceReload && (s_regions != null)) return;

        GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        // deep crawl through subregion, countries and adm1.  Use for Georegion menu.
        s_regions = geolocaleDb.getRegions(true);
    }

    private static void populateShallow(Connection connection, boolean forceReload) {

        if (!forceReload && (s_geolocales != null)) return;

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

        Collections.sort(s_geolocales);

        s_adm1s = (ArrayList<Adm1>) geolocaleDb.getAdm1s();

        s_adm1s.forEach(adm1 -> s_adm1_map.put(adm1.getName(), adm1.getCountry(), adm1));

        s_country_map = geolocaleDb.getCountries().stream().collect(Collectors.toMap(Country::getName, Function.identity()));
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
            A.log("GeolocaleMgr.getPlaceNames(text) initializing...");
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

    public static Geolocale getGeolocale(String name) {
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
        if (geolocale != null) {
            //A.log("getGeolocale(name) BEING USED for adm1. Searching for Adm1 by name not allowed. Stop it. name:" + name);
            // This can be used in non-critical ways. For instance, Place Names search box. Will almost always be right.
            return geolocale;
        }

        return null;
    }

    // To be removed... because not unique.
    public static Geolocale getAdm1(String adm1Name) {
        for (Geolocale geolocale : s_geolocales) {
            if ("adm1".equals(geolocale.getGeorank()) && geolocale.getName().equals(adm1Name)) return geolocale;
        }
        return null;
    }

    public static Adm1 getAdm1(int geolocaleId) {
        Geolocale geolocale = getGeolocale(geolocaleId);
        if (geolocale != null) return (Adm1) geolocale;
        return null;
    }

    public static Geolocale getGeolocale(int geolocaleId) {
        if (!AntwebMgr.isPopulated()) return null;

        //A.log("getGeolocale() id:" + geolocaleId);

        int c = 0;
        for (Geolocale geolocale : s_geolocales) {
            ++c;
            //if (c % 1000 == 0) A.log("getGeolocale() c:" + c);


            if (geolocaleId == geolocale.getId()) {
                return geolocale;
            }
        }
        return null;
    }

    public static Geolocale getGeolocale(String name, String georank) {

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

    public static ArrayList<Geolocale> getLiveGeolocales() {
        if (s_geolocales == null) return null; // Could happen due to server initialization.

        return s_geolocales.stream()
                .filter(Geolocale::getIsLive)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static ArrayList<Geolocale> getGeolocales(String georank) {
        return GeolocaleMgr.getGeolocales(georank, null);
    }

    public static ArrayList<Geolocale> getGeolocales(String georank, boolean isValid) {
        if (isValid) return GeolocaleMgr.getGeolocales(georank, "true");
        else return GeolocaleMgr.getGeolocales(georank, "false");
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

    /**
     * Get all geolocales that match one or more conditions
     *
     * @param georank filter geolocales by georank. If null, get all georanks
     * @param isValid "true" to select only valid geolocales, "false" or null to select all.
     * @return An ArrayList of geolocales that match the parameters
     */
    public static @Nullable ArrayList<Geolocale> getGeolocales(String georank, String isValid) {

        //A.log("getGeolocales(" + georank + ", " + isValid + ") " + AntwebUtil.getShortStackTrace());

        AntwebMgr.isPopulated();

        if (s_geolocales == null) return null; // Could happen due to server initialization.

        Stream<Geolocale> geolocaleStream = s_geolocales.stream();

        // A non "true" value for isValid will return all.
        if ("true".equals(isValid)) {
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

    public static ArrayList<Geolocale> getValidCountries() {
        ArrayList<Geolocale> validCountries = GeolocaleMgr.getValidGeolocales("country");
//      ArrayList<Geolocale> validCountries = GeolocaleMgr.getGeolocales("country", true);
        Collections.sort(validCountries);
        return validCountries;
    }

    public static ArrayList<Geolocale> getValidAdm1s() {
        return s_adm1s.stream().filter(Adm1::isValid).collect(Collectors.toCollection(ArrayList::new));
//        return GeolocaleMgr.getGeolocales("adm1", true);
    }

    public static ArrayList<Geolocale> getAllAdm1s() {
        return GeolocaleMgr.getGeolocales("adm1", false);
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

    public static Country getCountry(String name) {
        if (name == null) return null;
        if (s_regions == null) return null;
//A.log("r:" + s_regions);
        for (Region region : s_regions) {
            for (Subregion subregion : region.getSubregions()) {
//A.log("s:" + subregion);
                for (Country country : subregion.getAllCountries()) {
//A.log("c:" + country);
                    if (name.equals(country.getName())) return country;
                }
            }
        }
        return null;
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
                    countryName = (new org.calacademy.antweb.Formatter()).removeSpaces(countryName);
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

    public static Geolocale getAnyCountry(String countryName) {
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
        if (s_country_map == null) {
            return null;
        }
        Country matchingCountry = s_country_map.get(country);

        if (matchingCountry == null) {
            return null;
        }

        if (matchingCountry.isValid()) {
            return matchingCountry;
        }

        // country is not valid, return the Country that this points to
        return s_country_map.get(matchingCountry.getValidName());
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
        Country country = s_country_map.get(name);
        if (country == null) { return false; }
        return country.isValid();
//      ArrayList<Geolocale> validCountries = getValidCountries();
//      for (Geolocale geolocale : validCountries) {
//        if (geolocale.getName().equals(name))
//          return true;
//      }
//      return false;
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
        return s_adm1s.stream().map(adm1 -> (Geolocale) adm1).collect(Collectors.toCollection(ArrayList::new));
//      return GeolocaleMgr.getGeolocales("adm1");
    }

    public static ArrayList<Geolocale> getAdm1sWithSpecimen() {
        ArrayList<Geolocale> adm1sWithSpecimen = new ArrayList<>();
        ArrayList<Geolocale> adm1s = GeolocaleMgr.getGeolocales("adm1");
        for (Geolocale adm1 : adm1s) {
            if (adm1.getSpecimenCount() > 0) {
                adm1sWithSpecimen.add(adm1);
            }
        }
        return adm1sWithSpecimen;
    }

    public static Geolocale inferCountry(String adm1Name) {
        // Will only return if unique
        ArrayList<Geolocale> adm1s = getAdm1s();
        if (adm1s == null) return null; // Could happen due to server initialization.
        Geolocale adm1 = null;
        for (Geolocale geolocale : adm1s) {
            if (adm1Name == null) return null;
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
            s_log.warn("getAnyAdm1(" + adm1Name + ", " + countryName + ") must included countryName.");
            return null;
        }
        List<Adm1> adm1s = s_adm1s;
        if (adm1s == null) return null; // Could happen due to server initialization.

        return s_adm1_map.get(adm1Name, countryName);
    }

    /** Only from valid countries!
     * @param adm1Name The adm1 to search for
     * @param countryName The country the Adm1 is in. Will be converted to valid country if invalid
     * @return The matching Adm1 from the valid country or null if not found
     */
    public static @Nullable Geolocale getAdm1(String adm1Name, String countryName) {
        Country country = GeolocaleMgr.getValidCountry(countryName);
        if (country == null) return null; // Could be server initializing.

        Adm1 matching_adm1 = s_adm1_map.get(adm1Name, country.getName());

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

        Adm1 matching_adm1 = s_adm1_map.get(adm1, country);

        if (matching_adm1 == null) {
            return null;
        }

        if (matching_adm1.isValid()) {return matching_adm1;}

        Adm1 valid_adm1 = s_adm1_map.get(matching_adm1.getValidName(), country);

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

    public static @Nullable String getGeolocaleBioregion(String countryName, String adm1Name) {
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

    private static ArrayList<Country> islands = new ArrayList<>();

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
}


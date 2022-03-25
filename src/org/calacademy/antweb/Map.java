package org.calacademy.antweb;

import java.sql.Connection;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.SpecimenDb;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/*

Project name	Project ID	Billing account	Billing account ID	Actions 
AntwebMaps	antwebmaps	Antweb Billing Account	00B8DA-F275DE-514F4E

Project ID:      project-674782366643
Project Number:  674782366643

Clicked on "Add Billing" Aug 21, 2018, 5:46 PM

APIs associated:
  Directions API
  Distance Matrix API
  Elevation API
  Javascript Dynamic Maps
  Places API
  Geocoding API

Initial error message on maps linked to here:
  https://developers.google.com/maps/documentation/javascript/error-messages?utm_source=maps_js&utm_medium=degraded&utm_campaign=billing#api-key-and-billing-errors

Google for Non-profits:   
  http://www.google.com/earth/outreach/grants/software/mapsapi.html
  https://support.google.com/nonprofits/answer/3367237?hl=en&ref_topic=3247651
  
Google Cloud Platform for antwebmaps
  https://console.cloud.google.com/home/dashboard?project=antwebmaps

https://console.cloud.google.com/billing/00B8DA-F275DE-514F4E?folder&organizationId

re.mark.johnson@gmail.com and bfisher@calacademy.org are set up as billing administrators.  

According to our new Display Map Counter (counting the number of times that the googleMapInclude.jsp 
  is included in a requested page) was about 70,000 today. That means about 2,100,000 maps per month. 
  This puts us about 4x above the minimum required to consider "Contact Sales for volume discounts".
    
*/

public class Map {

    private ArrayList<String> chosenList;
    public static int displayMapCount = 0;
    private static HashMap<String, Integer> displayMapHash = new HashMap<>();

    private String title;
    private String subtitle;

    private String info;

    protected String staticMapParams;
    protected ArrayList<Coordinate> points = new ArrayList<>();
    protected ArrayList mapSpecimens = new ArrayList();

    protected String mapName = "";
    protected String googleMapFunction = "";

    protected boolean isLocality = false;
    protected boolean isCollection = false;

    protected String taxonName = "";
    protected String localityName = "";
    protected String localityCode = "";
    protected String collectionCode = "";

    boolean mapLocalities = false;
    public static int MAXMAPPOINTS = 5000;  // was safely 1000, but we miss some results from madagasy.  Then was 5000

    // Now it is set to allow all again... will we find memory problems?
    public String getTaxonName() {
        return taxonName;
    }

    public String getLocalityCode() {
        return localityCode;
    }

    public String getCollectionCode() {
        return collectionCode;
    }

    private Date cached;

    private static Log s_log = LogFactory.getLog(Map.class);


    public Map() {
        super();
        if (AntwebProps.isDevOrStageMode()) MAXMAPPOINTS = 50000;
    }

    public Map(Taxon taxon, Connection connection) {
        this(taxon, ProjectMgr.getProject(Project.ALLANTWEBANTS), connection, MAXMAPPOINTS, false);
    }

    public Map(Taxon taxon, LocalityOverview overview, Connection connection) {
        this(taxon, overview, connection, MAXMAPPOINTS, false);
    }

    public Map(Taxon taxon, LocalityOverview overview, Connection connection, boolean geolocaleFocus) {
        // geolocaleFocus passed all the way from the client. By default we show all specimens of a taxa in a geolocale.
        this(taxon, overview, connection, MAXMAPPOINTS, geolocaleFocus);
    }

    public Map(Taxon taxon, LocalityOverview overview, Connection connection, int maxMapPoints) {
        this(taxon, overview, connection, maxMapPoints, false);
    }

    private Map(Taxon taxon, LocalityOverview overview, Connection connection, int maxMapPoints, boolean geolocaleFocus) {
        // taxon or specimen

        setPoints(taxon, overview, connection, maxMapPoints, geolocaleFocus);

        //long thisTime = new GregorianCalendar().getTimeInMillis();
        long thisRand = new Random().nextLong();

        taxonName = taxon.getPrettyName();

        setMapName("map" + thisRand);
        setGoogleMapFunction();
    }

    public Map(Locality locality) {
        super();

        isLocality = true;
        setPoints(locality);

        locality.setMap(this);

        //long thisTime = new GregorianCalendar().getTimeInMillis();
        long thisRand = new Random().nextLong();

        setMapName("map" + thisRand);
        //A.log("Map(Locality) name is " + getMapName() + " for map:" + this);
        setGoogleMapFunction();
    }

    public Map(Collection collection) {
        super();

        isCollection = true;
        setPoints(collection);

        collection.setMap(this);

        //long thisTime = new GregorianCalendar().getTimeInMillis();
        long thisRand = new Random().nextLong();

        setMapName("map" + thisRand);
        //s_log.warn("Map(Locality) name is " + getMapName() + " for map:" + this);
        setGoogleMapFunction();
    }

    public Map(ArrayList<String> specimens, Connection connection) {
        super();
        setChosenList(specimens);
        if (specimens != null && specimens.size() > 0) {
            long thisTime = new GregorianCalendar().getTimeInMillis();
            setMapName("map" + thisTime);

            setPoints(specimens, connection);
            setGoogleMapFunction();
        }
    }

    public Map(ArrayList<String> specimens, boolean mapLocalities, int searchResultsSize, int localitySize, String info, Connection connection) {
        super();
        setChosenList(specimens);
        // This is not the same as a Map(Locality). These are specimen codes but one for each locality to be mapped.
        setIsMapLocalities(mapLocalities);
        setInfo(info);
        if (specimens != null && specimens.size() > 0) {
            long thisTime = new GregorianCalendar().getTimeInMillis();

            setMapName("map" + thisTime);

            setPoints(specimens, searchResultsSize, localitySize, connection);
            setGoogleMapFunction();
        }
    }

    public String getStaticMapParams() {
        return staticMapParams;
    }

    public void setStaticMapParams(String staticMapParams) {
        this.staticMapParams = staticMapParams;
    }

    public void setStaticMapParams(Taxon taxon, Overview overview) {
        ArrayList terms = new ArrayList();

        this.staticMapParams = "";

        if (Utility.notBlank(taxon.getSubfamily())) {
            terms.add("subfamily%3d%27" + taxon.getSubfamily() + "%27");
        }

        if (Utility.notBlank(taxon.getGenus())) {
            terms.add("genus%3d%27" + taxon.getGenus() + "%27");
        }

        if (Utility.notBlank(taxon.getSpecies())) {
            terms.add("species%3d%27" + taxon.getSpecies() + "%27");
        }

        if (Utility.notBlank(taxon.getSubspecies())) {
            terms.add("subspecies%3d%27" + taxon.getSubspecies() + "%27");
        }

        if (Utility.notBlank(overview.toString()) && !overview.equals(Project.WORLDANTS)) {
            //String term = "project+like+%27%25" + project + "%25%27";
            String term = "project+like+%27%25" + overview + "%25%27";
            terms.add(term);
            s_log.warn("setStaticMapParams() CORRECT? overview:" + overview + " terms:" + terms);
        }

        String andedTerms = Utility.andify(terms);
        andedTerms = andedTerms.replaceAll(" ", "+");

        //A.log("setStaticMapParams() taxonName:" + taxon.getTaxonName() + " + andedTerms:" + andedTerms);
        setStaticMapParams(andedTerms);
    }

    public boolean hasPoints() {
        return getPoints() != null && getPoints().size() > 0;
    }

    public ArrayList<Coordinate> getPoints() {
        return points;
    }

    public void setPoints(ArrayList<Coordinate> points) {
        this.points = points;
    }

    // MarkMap
    public void setPoints(Collection collection) {
        this.points = new ArrayList<>();
        Locality loc = collection.getLocality();
        if (loc == null) {
            //s_log.warn("setPoints() loc is null for collection:" + collection);
            return;
        }
        float thisLon = loc.getDecimalLongitude();
        float thisLat = loc.getDecimalLatitude();
        if (thisLon != 0.0 && thisLat != 0.0) {
            points.add(new Coordinate(thisLon, thisLat));
            //s_log.warn("setPoints(locality) lon:" + thisLon + " lat:" + thisLat);
        } else {
            //s_log.warn("setPoints(locality) no points");
        }
        collectionCode = collection.getCode();  // What?
    }

    // MarkMap
    public void setPoints(Locality locality) {
        this.points = new ArrayList<>();

        float thisLon = locality.getDecimalLongitude();
        float thisLat = locality.getDecimalLatitude();
        if (thisLon != 0.0 && thisLat != 0.0) {
            points.add(new Coordinate(thisLon, thisLat));
            //s_log.warn("setPoints(locality) lon:" + thisLon + " lat:" + thisLat);
        } else {
            //s_log.warn("setPoints(locality) no points");
        }
        localityCode = locality.getLocalityCode();
        localityName = locality.getLocalityName();
        //A.log("setPoints(localityName) locality:" + localityName);
    }


    private static boolean flip = false;

    private boolean isFlip() {
        flip = !flip;
        return flip;
    }

    public void setPoints(ArrayList<String> specimens, Connection connection) {
        // usually the specimenCount is the same size as specimens.size(), but not in the case of locality mapping.
        setPoints(specimens, specimens.size(), 0, connection);
    }

    public void setPoints(ArrayList<String> specimens, int specimenCount, int localityCount, Connection connection) {

        this.points = new ArrayList<>();
        if (specimens.size() > 0) {
            String firstClause = SpecimenDb.getFlagCriteria();
            if (specimens.size() == 1) firstClause = " 1 = 1 ";
            String theQuery = "select decimal_latitude, decimal_longitude, code, country, adm1, taxon_name, genus, species, subspecies, localityname, localitycode" 
              + " from specimen where " + firstClause;

            if (AntwebProps.isDevMode()) {
                StatusSet statusSet = new StatusSet(StatusSet.VALID_EXTANT);
                String statusCriteria = statusSet.getCriteria("specimen");
                //A.log("setPoints() A statusCriteria:"+statusCriteria);
               // Do we like this? Want to add to theQuery?
            }

            theQuery += " and code in ";
            
            //A.log("setPoints(ArrayList<String> specimens 1");            
            
            String thisSpec;
            
            theQuery += "(";
            
            int i = 0;
            for (String specimen : specimens) {
                if (i >= 1) theQuery += ",";
                i = i + 1;
                theQuery += "'" + specimen + "'";
            }
            theQuery += ")";
            theQuery += " order by species";
            
            MAXMAPPOINTS = 8000;
            //MAXMAPPOINTS = 10000;
            
			//MAXMAPPOINTS = 50000;
			//MAXMAPPOINTS = 25000;
  
            //A.log("setPoints() maxMapPoints:" + MAXMAPPOINTS);
            
			Statement stmt = null;
			ResultSet rset = null;
			try {
				stmt = connection.createStatement();
				stmt = DBUtil.getStatement(connection, "Map.setPoints(specimens)");
				rset = stmt.executeQuery(theQuery);            
                float thisLat, thisLon;
                //String thisGenus, thisSpecies, thisSubspecies, thisCode, thisName;
                Specimen specimen;
                String key;
                HashMap tracker = new HashMap();
                int nonUniqueLocalities = 0;
                int keyCount = 0;
                int pointCount = 0;
                int totalPointCount = 0;  // for if we exceed MAXMAPPOINTS
                int chosenListSize = 0;
                boolean isDiscard = false;

                while (rset.next()) {
                    ++chosenListSize;
                    if (pointCount >= MAXMAPPOINTS) isDiscard = true;  // to avoid java.lang.OutOfMemoryError: Java heap space

                    thisLat = rset.getFloat("decimal_latitude");
                    thisLon = rset.getFloat("decimal_longitude");
                    String taxonName = rset.getString("taxon_name");
                    
                    if (thisLon != 0.0 && thisLat != 0.0) {
                        key = taxonName + ":" + thisLon + ":" + thisLat;
                        
						String adm1 = rset.getString("adm1");
						//if ("Minnesota".equals(adm1)) A.slog("setPoints() adm1:" + adm1);
												
                        if (tracker.containsKey(key)) {
                            keyCount = (Integer) tracker.get(key);
                            ++keyCount;
                            tracker.put(key, keyCount);
                            ++nonUniqueLocalities;
                            
                            //Uncomment the s_log to see a list of the nonUniqueLocalities... 
                            //Maybe modify ObjectMapsDb.genMuseumObjectMap(Museum museum) so you can tell which are for which musuem.
                            //s_log.warn("setPoints() tracker added key: " + key + " keyCount:" + keyCount);
                            // build a query like: select museum, taxon_name, localitycode, decimal_longitude, decimal_latitude from specimen where taxon_name = "formicinaepolyrhachis schistacea" and decimal_longitude = "39.82587" order by decimal_longitude;
                            // to see the offending records.
                        } else {
                            specimen = new Specimen();
                            specimen.setCode(rset.getString("code"));
                            specimen.setName(taxonName);
                            specimen.setGenus(rset.getString("genus"));
                            specimen.setSpecies(rset.getString("species"));
                            specimen.setSubspecies(rset.getString("subspecies"));
                            String localityCode = rset.getString("localitycode");
                            String localityName = rset.getString("localityname");    
                            if (localityCode != null) specimen.setLocalityCode(HttpUtil.encode(localityCode));
                            if (localityName != null) specimen.setLocalityName(HttpUtil.encode(localityName));
                                                    
                            tracker.put(key, 1);
                            //A.log("setPoints(ArrayList<String> specimens coord:" + coord); // isDiscard:" + isDiscard);
                            if (!isDiscard) {
                              Coordinate coord = new Coordinate(thisLon, thisLat);
                              points.add(coord);
                              ++pointCount;
                              ++totalPointCount;
                              mapSpecimens.add(specimen);
                            } else {
                              ++totalPointCount;
                            }
                        }
                    }
                }

                boolean debugMap = false;
                //debugMap = (pointCount != localityCount) || tracker.size() > 0;
                if (debugMap) A.slog("setPoints(ArrayList<Specimens>, Connection) "
                  + " specimenCount:" + specimenCount + " chosenSpecimens:" + specimens.size()
                  + " chosenListSize:" + chosenListSize + " pointCount:" + pointCount + " localityCount:" + localityCount + " keyCount:" + keyCount); // + " theQuery:" + theQuery);

                String localityStr = "";
                if (localityCount > 0) localityStr = " Localities:" + localityCount;
                if (specimenCount > 1)  // Looks silly to report Specimens:1, Mappable:1
			      if (totalPointCount == pointCount) {
			        String subtitle = "Specimens:" + specimenCount + localityStr + " Unique Mappable:" + pointCount;
			        if (pointCount == MAXMAPPOINTS) subtitle += " (maximum)";
  					setSubtitle(subtitle);
				  } else {
                    setSubtitle("Specimens:" + specimenCount + localityStr  + " Mapped:" + pointCount + " (of " + totalPointCount + ")");                    
				  }
				addToInfo(" chosenListSize:" + chosenListSize);
                addToInfo(" mapPoints:" + pointCount);
                addToInfo(" totalPointCount:" + totalPointCount);
                if (nonUniqueLocalities > 0) addToInfo(" nonUniqueLocalities:" + nonUniqueLocalities);
            } catch (SQLException e) {
                s_log.warn("setPoints(ArrayList<Specimen>, Connection) in Map: e:" + e);
                //org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            } finally {
                DBUtil.close(stmt, rset, this, "Map.setPoints(specimens)");
            }
        }
    }

    public static String reportStats() {
      return "nfSM:" + nonFucusedSubfamilyMaps + " aNfSM:" + adm1NonFucusedSubfamilyMaps;
    }
    public static int nonFucusedSubfamilyMaps = 0;
    public static int adm1NonFucusedSubfamilyMaps = 0;

    private void setPoints(Taxon taxon, LocalityOverview overview, Connection connection, int maxMapPoints, boolean geolocaleFocus) {
        
        boolean persist = taxon.isSubfamily() && !geolocaleFocus; // or genus?
        if (persist) {
          ++nonFucusedSubfamilyMaps;
          // Oops, not so easy to persist a map, is it?
          // We could persist these maps and fetch them without calculation. 
        }
        if (taxon.isSubfamily() && !geolocaleFocus && overview instanceof Adm1) {
          ++adm1NonFucusedSubfamilyMaps;
          // We could choose to not display these. Would do so in the BrowseAction class.
        }

        // a value of 1 indicates that we want a single record to verify that a map should exist.  Do not sort to be faster.
        // a value of 0 indicates that just run it without sorting.  Assumed to be small.
        // a value of 5000 will set a limit and have those results ordered to increase randomness.

        //A.log("setPoints() taxon:" + taxon + " localityOverview:" + overview + " maxMapPoints:" + maxMapPoints + " geolocaleFocus:" + geolocaleFocus);
     
        if (overview == null) {
          s_log.info("setPoints() overview:null");
          return;
        }
        if (taxon == null) {
          s_log.info("setPoints(taxon, project, connection, maxMapPoints) taxon is null.  WSS");
          return;
        }

        String name = overview.getName();

        //A.log("setPoints(taxon, localityOverview, connection) taxon:" + taxon + " overview:" + overview);
 
        String query;
        ArrayList<String> terms = new ArrayList<>();

        String locality;

        boolean useProject = overview instanceof Project;
          if (name.equals(Project.WORLDANTS) || name.equals("ALLANTWEBANTS") || "null".equals(name)) useProject = false;
        // geolocaleFocus is passed all the way from the web page to indicate query restiction and resulting zoom.
        boolean useGeolocale = geolocaleFocus && overview instanceof Geolocale;
        boolean useBioregion = overview instanceof Bioregion;

        query = "select sp.decimal_longitude, sp.decimal_latitude, sp.code, sp.taxon_name, sp.genus, sp.species, sp.subspecies, localitycode "
          + " from specimen as sp";

        if (useProject) query += ", proj_taxon"; 
        //if (useGeolocale) query += ", geolocale_taxon";
        
        query += " where " + SpecimenDb.getFlagCriteria();
        if (useProject) query += " and proj_taxon.taxon_name = sp.taxon_name";
        //if (useGeolocale) query += " and geolocale_taxon.taxon_name = sp.taxon_name";

        if (Utility.notBlank(taxon.getSubfamily())) {
          terms.add("sp.subfamily= '" + AntFormatter.escapeQuotes(taxon.getSubfamily()) + "'");
        }
        if (Utility.notBlank(taxon.getGenus())) {
          terms.add("sp.genus= '" + AntFormatter.escapeQuotes(taxon.getGenus())  + "'");
        }
        if (Utility.notBlank(taxon.getSpecies())) {
          terms.add("sp.species= '" + AntFormatter.escapeQuotes(taxon.getSpecies())  + "'");
        }
        if (Utility.notBlank(taxon.getSubspecies())) {
          terms.add("sp.subspecies= '" + AntFormatter.escapeQuotes(taxon.getSubspecies())  + "'");
        }

        if (terms.size() == 0) {
          return;
        }
        //terms.add("sp.decimal_longitude is not null");
        //terms.add("sp.decimal_latitude is not null");

        if (useBioregion) terms.add("sp.bioregion = '" + name + "'");  

        if (useProject) {
          locality = overview.getLocality();
          if (locality != null && locality.length() > 0 && !locality.equals("null"))  {
            terms.add("sp." + locality);
          }    
          terms.add("proj_taxon.project_name = '" + name + "'");
        }

        if (useGeolocale) {
          //terms.add("geolocale_taxon.geolocale_id = " + ((Geolocale) overview).getId());
          if (overview instanceof Country) {
            terms.add("sp.country = '" + overview.getName() + "'");
          }
          if (overview instanceof Adm1) {
            terms.add("sp.country = '" + ((Adm1) overview).getParent() + "'");          
            terms.add("sp.adm1 = '" + overview.getName() + "'");
          }          
        }

        if (AntwebProps.isDevMode()) {  // if statusSet != null
            //StatusSet statusSet = new StatusSet(StatusSet.VALID_EXTANT);
            StatusSet statusSet = new StatusSet(StatusSet.VALID_WITH_FOSSIL);
            String statusCriteria = statusSet.getCriteria("sp");
            //A.log("setPoints() B statusCriteria:" + statusCriteria);

            terms.add(statusCriteria);
        }

        query += " and " + Utility.andify(terms);

        if (maxMapPoints > 1) {
            query += " order by SECOND(sp.created)";
                // This is to make the set as geographically random as possible in case of data truncation
            //query += " limit " + maxMapPoints;
        }

        //A.log("setPoints(taxon, overview, connection, maxPoints, geolocaleFocus) query:" + query);

        //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
        //A.log("setPoints(taxon, overview, connection, maxPoints) overview:" + name + " locality:" + overview.getLocality());
  
  /*
        String logThisOne = ")  and sp.subfamily= 'ponerinae' order";
        // for instance: https://www.antweb.org/images.do?subfamily=ponerinae&rank=subfamily&adm1Name=Chihuahua&countryName=Mexico
        if (query.contains(logThisOne)) {
          s_log.warn("setPoints() investigate Too much, too slow. taxon:" + taxon + " overview:" + overview + " query:" + query);
          //AntwebUtil.logShortStackTrace(5);
        }
  */
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "Map.setPoints(taxon)");
            rset = stmt.executeQuery(query);
            //Extent theExtent = null;
            float lat, lon;
            String taxonName, code, genus, species, subspecies, localityCode;
            Specimen spec;
            String key;
            HashMap distinctLocalities = new HashMap();
            HashMap distinctUnmappableLocalities = new HashMap();
            int keyCount = 0;
            int pointCount = 0;
            int counter = 0;
            int distinctMappableCount = 0;
            int distinctUnmappableCount = 0;
            while (rset.next()) {
                ++counter;
                if (pointCount >= maxMapPoints) continue;  // to avoid java.lang.OutOfMemoryError: Java heap space  
						  
				lon = rset.getFloat("decimal_longitude");
				lat = rset.getFloat("decimal_latitude");
				//taxonName = rset.getString("taxon_name"); 
				code = rset.getString("code");
				genus = rset.getString("genus");
				species = rset.getString("species");
				subspecies = rset.getString("subspecies");
				localityCode = rset.getString("localitycode");
				if (lon != 0.0 && lat != 0.0) {
					key = lon + ":" + lat; //taxonName + ":" + 
					if (distinctLocalities.containsKey(key)) {
						keyCount = (Integer) distinctLocalities.get(key);
						++keyCount;
						distinctLocalities.put(key, keyCount);
					} else {
						distinctLocalities.put(key, 1);
						++distinctMappableCount;
						if (distinctMappableCount <= MAXMAPPOINTS) {  // We only map so many... // was: counter
						  points.add(new Coordinate(lon, lat));
						  spec = new Specimen();
						  spec.setCode(code);
						  spec.setGenus(genus);
						  spec.setSpecies(species);
						  spec.setSubspecies(subspecies);
						  mapSpecimens.add(spec);
						  ++pointCount;
						} else {
						  // We should be handling > 7500...  Currently our largest subfamilies are close to 2000.
						}
					}
				} else {
					if (distinctUnmappableLocalities.containsKey(localityCode)) {
						//keyCount = ((Integer) distinctLocalities.get(localityCode)).intValue();
						//++keyCount;
						//distinctLocalities.put(localityCode, Integer.valueOf(keyCount));
					} else {
						++distinctUnmappableCount;
						distinctUnmappableLocalities.put(localityCode, 1);
                    }
				}
            }// end while

            // If the decimal longitude and latitude are null, no google map function will be set

            //if (false && (counter == 0) && AntwebProps.isDevMode()) 
              if (true) A.log("setPoints(taxon, project, connection, maxMapPoints) taxon:" + taxon + " name:" + name
              + " counter:" + counter + " pointCounter:" + pointCount + " keyCount:" + keyCount + " query:" + query);
              
            // Taxon maps do not have a title  
              
            int localityCount = distinctMappableCount + distinctUnmappableCount;
            String subtitle = counter + " Specimens, " + localityCount + " Localities, " + pointCount + " Mappable";
            if (distinctMappableCount == MAXMAPPOINTS) subtitle += " (maximum)";
            setSubtitle(subtitle);
             
            setInfo("Specimen:" + counter + " localities:" + localityCount + " distinctMappableCount:" + distinctMappableCount + " distinctUnmappableCount:" + distinctUnmappableCount + " " + reportStats());
              //setPointCounter(innerCounter);

        } catch (SQLException e) {
            s_log.error("setPoints(5) e:" + e + " taxon:" + taxon + " name:" + name + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, this, "Map.setPoints(taxon)");
        }
    }
 
    public String getGoogleMapFunction() {
        return googleMapFunction;
    }
    public void setGoogleMapFunction(String googleMapFunction) {
        this.googleMapFunction = googleMapFunction;
    }
    
    /**
     * Creates a function string to execute in JavaScript
     * The googleMapFunction to set.
     */
    public void setGoogleMapFunction() {
         
        //A.log("setGoogleMapFunction() points:" + points);
        //	AntwebUtil.infoStackTrace();
        
        StringBuffer theString = null;
        String googleString = null;
        if (getPoints() != null && getPoints().size() > 0) {
          //ArrayList clearPoints = clearZeros(getPoints());
          String latArray = getJavaScriptArray(getPoints(),"lat");
          String lonArray = getJavaScriptArray(getPoints(),"lon");
          if (latArray != null && lonArray != null) {
            
              theString = new StringBuffer();

              if (isLocality) {
                theString.append("drawGoogleMapLocality(");
              } else if (isCollection) {
                theString.append("drawGoogleMapCollection(");      
              } else if (isMapLocalities()) {
                theString.append("drawGoogleMapLocalities(");      
              } else {                
                theString.append("drawGoogleMapSpecimens(");
              }
              
              if (getPoints().size() > 1) {
                theString.append("'small', ");
                theString.append("'" + getMapName() + "',");
                theString.append(latArray);
                theString.append(",");
                theString.append(lonArray);
              } else {
                // If it is a single point, as would be a locality or collection.
                theString.append("'small', ");
                theString.append("'" + getMapName() + "',");
                theString.append(getPoints().get(0).getLat());
                theString.append(",");
                theString.append(getPoints().get(0).getLon());
              }
              theString.append(",");
//A.log("setGoogleMapFunction() isLocality:" + isLocality + " isCollection:" + isCollection + " isMapLocality:" + isMapLocalities());
//if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
 
              if (isLocality) {
                  String encodedLocalityName = null;
                  if (localityName != null) encodedLocalityName = HttpUtil.encode(localityName);
                  String encodedLocalityCode = null;
                  if (localityCode != null) encodedLocalityCode = HttpUtil.encode(localityCode);
                  theString.append("new Array('" + encodedLocalityName + "'),new Array('" + encodedLocalityCode + "')");
              } else if (isCollection) {
                  theString.append("new Array('" + collectionCode + "')");
              } else if (isMapLocalities()) {
                //A.slog("localityNames:" + getJavaScriptSpecimenArray(getMapSpecimens(), "localityname"));
                  theString.append(getJavaScriptSpecimenArray(getMapSpecimens(), "localityname"));
                  theString.append(",");
                //A.slog("localityCodes:" + getJavaScriptSpecimenArray(getMapSpecimens(), "localitycode"));
                  theString.append(getJavaScriptSpecimenArray(getMapSpecimens(), "localitycode"));
              } else { // specimens
                  theString.append(getJavaScriptSpecimenArray(getMapSpecimens(), "name"));
                  theString.append(",");
                  theString.append(getJavaScriptSpecimenArray(getMapSpecimens(), "code"));
                  theString.append(",");
                  theString.append(getJavaScriptSpecimenArray(getMapSpecimens(), "images"));
              }
              theString.append(", '" + AntwebProps.getDomainApp() + "/'");

              theString.append(");");

              googleString = theString.toString();
          }
        } else {
          // AntwebUtil.logStackTrace();
          //A.log("Map.setGoogleMapFunction() no points found. localityCode:" + localityCode  + " collectionCode:" + collectionCode);
        }

        //A.log("setGoogleMapFunction() googleString:" + googleString.substring(0, 100) + "... locality:" + isLocality + " collection:" + isCollection + " points:" + getPoints().size());
        //A.log("setGoogleMapFunction() points.size:" + getPoints().size() + " locality:" + isLocality + " function:" + googleString);
        //A.slog("setGoogleMapFunction() points.size:" + getPoints().size());

        setGoogleMapFunction(googleString);
    }

//private static String s_googleString;

    public String getJavaScriptArray(ArrayList<Coordinate> points, String coord) {
        
        StringBuffer theArrayString = new StringBuffer();
        boolean foundPoint = false;
        theArrayString.append("new Array(");
        
//        Iterator theIter = points.iterator();
        //Coordinate thisCoord;
        float thisFloat;
        
//        while (theIter.hasNext()) {
        for (Coordinate coords : points) {
//            coords = (Coordinate) theIter.next();
            if (coord.equals("lat")) {    
                thisFloat = coords.getLat();
            } else {
                thisFloat = coords.getLon();
            }
            if (thisFloat != 0.0) {
                
                if (foundPoint) {
                    theArrayString.append(",");
                } else {
                    foundPoint = true;
                }
                theArrayString.append(Float.valueOf(thisFloat));
            }
        }

        theArrayString.append(")");
       // A.slog("getJavascriptArray() " + coord + ":" + points.size());
        
        if (foundPoint) {
            return theArrayString.toString();
        } else {
            return null;
        }
    }
    
    public String getJavaScriptSpecimenArray(ArrayList<Specimen> specimens, String field) {
        
        StringBuffer theArrayString = new StringBuffer();
        boolean foundPoint = false;
        theArrayString.append("new Array(");
        
//        Iterator theIter = specimens.iterator();
//        Specimen thisSpecimen;
        String value="";
        Formatter format = new Formatter();
        SpecimenImage specImage;
//        while (theIter.hasNext()) {
        //int i = 0;
        for (Specimen specimen : specimens) {
//            thisSpecimen = (Specimen) theIter.next();
            //i = i + 1;
            switch (field) {
                case "name":
                    value = format.capitalizeFirstLetter(specimen.getGenus()) + " " + specimen.getSpecies();
                    if (specimen.getSubspecies() != null) value += " " + specimen.getSubspecies();
                    break;
                case "code":
                    value = specimen.getCode();
                    break;
                case "localitycode":
                    value = specimen.getLocalityCode();
                    break;
                case "localityname":
                    value = specimen.getLocalityName();
                    break;
                case "images":
                    if (specimen.getImages() != null) {
                        specImage = specimen.getImages().get("p1");
                        if (specImage != null) {
                            value = specImage.getLowres();
                        }
                    } else {
                        value = "";
                    }
                    break;
                default:
                    //i = i - 1; // because we didn't find something to add.
                    break;
            }
            if (foundPoint) {
                theArrayString.append(",");
            } else {
                foundPoint = true;
            }
            theArrayString.append("'" + value + "'");
        }

        theArrayString.append(")");
        
        if (foundPoint) {
            return theArrayString.toString();
        } else {
            return null;
        }
    }

    public String getMapName() {
        return mapName;
    }
    public void setMapName(String mapName) {
        this.mapName = mapName;
    }
    
    public boolean isLocality() {
      return isLocality;
    }

    public boolean isCollection() {
      return isCollection;
    }
    
    public int getPointCount() {
      return mapSpecimens.size();
    }
    
    public ArrayList getMapSpecimens() {
        return mapSpecimens;
    }
    public void setMapSpecimens(ArrayList mapSpecimens) {
        this.mapSpecimens = mapSpecimens;
    }


    public static void addToDisplayMapCount() {
      ++displayMapCount;
    }
    public static int getDisplayMapCount() {
      return displayMapCount;
    }

    public static void addToDisplayMapCount(String objectName) {
      Integer count = displayMapHash.get(objectName);
      if (count == null) {
        displayMapHash.put(objectName, 1);
      } else {
        count = count + 1;
        displayMapHash.put(objectName, count);
      }
      //A.log("addToDisplayMapCount() count:" + count);    
    }
    public static String getDisplayMapHashCounts() {
      String counts = "";
      int i = 0;
      for (String key : displayMapHash.keySet()) {
        ++i;
        if (i > 1) counts += ", ";
        counts += key + ":" + displayMapHash.get(key);
      }
      s_log.debug("getDisplayMapHashCounts() counts:" + counts);
      return counts;
    }    

    
    // ---
    
    public void setTitle(String title) {
      this.title = title;
    }
    public String getTitle() {
      return this.title;
    }

    public void setSubtitle(String subtitle) {
      this.subtitle = subtitle;
    }
    public String getSubtitle() {
      return this.subtitle;
    }
     

    public void setInfo(String info) {
      this.info = info;
    }
    public String getInfo() {
      return this.info;
    }
    public void addToInfo(String info) {
      if (this.info == null) this.info = info;
      else this.info += info;
    }
                
    public void setChosenList(ArrayList<String> chosenList) {
      this.chosenList = chosenList;
    }
    public ArrayList<String> getChosenList() {
      return this.chosenList;
    }

    public void setIsMapLocalities(boolean mapLocalities) {
      this.mapLocalities = mapLocalities;
    }
    public boolean isMapLocalities() {
      return mapLocalities;
    }

    public void setCached(Date date) {
      cached = date;
    }
    public Date getCached() {
      return cached;
    }

}



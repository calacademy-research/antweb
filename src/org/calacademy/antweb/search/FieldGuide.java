package org.calacademy.antweb.search;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Map;
import org.calacademy.antweb.Formatter;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class FieldGuide {
  /* This version of FieldGuide.java does not utilize PDF or XML */
    private static final Log s_log = LogFactory.getLog(FieldGuide.class);

    private ArrayList<Taxon> taxa;
    protected String title;
    protected Overview overview;
    //protected Project project = null;
    protected String rank;
    protected Extent extent;
    protected HashMap localities = new HashMap();

    private final int MAX_MARKERS = 50;

    public FieldGuide() {
       super();
    }

    public void setMembers(Connection connection, Overview overview) throws SQLException {
      setImages(connection, overview);
      setMaps(connection);
      setSpecimenData(connection);
    }
    
    public void setImages(Connection connection, Overview overview) throws SQLException {
        for (Taxon thisTaxon : getTaxa()) {
            //s_log.warn("getImages() taxonName:" + thisTaxon.getName() + " rank:" + thisTaxon.getRank());            
            //if (thisTaxon.getRank().equals("specimen")) {

            //thisTaxon.setImages(overview, false);
            thisTaxon.setImages(connection, overview, Caste.DEFAULT);
        }
    }

    public void setMaps(Connection connection) {
        for (Taxon thisTaxon : getTaxaArray()) {
        // for (Taxon thisTaxon : getTaxa()) {  // Concurrency exception risk
        
            //s_log.warn("getMaps() taxonName:" + thisTaxon.getName() + " rank:" + thisTaxon.getRank());            
            if (thisTaxon.getRank().equals("specimen")) {
                ArrayList specimenList = new ArrayList();
                specimenList.add(thisTaxon.getName());
                thisTaxon.setMap(new Map(specimenList, connection));
            } else {    
                //A.log("setMaps() taxon:" + thisTaxon + " project:" + project);
                if (overview instanceof LocalityOverview)
                  thisTaxon.setMap(new Map(thisTaxon, (LocalityOverview) overview, connection));
            }
        }
    }

    public void setSpecimenData(Connection connection) throws SQLException {
        for (Taxon thisTaxon : getTaxa()) {
            //s_log.warn("getMaps() taxonName:" + thisTaxon.getName() + " rank:" + thisTaxon.getRank());            
            if (thisTaxon.getRank().equals(Rank.SPECIES) || thisTaxon.getRank().equals(Rank.SUBSPECIES)) {
                thisTaxon.setHabitats(connection);
                thisTaxon.setMethods(connection);
                thisTaxon.setElevations(connection);
            }
        }
    }

    private Locality setLocalityForSpecimen(Taxon taxon, Connection connection) {
        Locality result = null;

        String locality = ((LocalityOverview) getOverview()).getLocality();
        String theQuery = "select localitycode from specimen where specimen.code='" + taxon.getName() + "'";

        if (locality != null && locality.length() > 0 && !locality.equals("null"))  {
            theQuery += " and " + locality;
        }
        
        //s_log.info("query:" + theQuery);
        String localityCode = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
            
            while (rset.next()) {
                localityCode = rset.getString("localitycode");
            }

            if (localityCode != null) {
              //s_log.info("looking up locality code: " + localityCode);
              result = new LocalityDb(connection).getLocality(localityCode);
            } 
        } catch (SQLException e) {
            s_log.warn("problem getting points " + theQuery + e);
        } finally {
          DBUtil.close(stmt, rset, this, "setLocalityForSpecimen()");
        }
                
        return result;
    }
    
/*  In versions of Antweb older than v7.33 you will find a method like this commented out...
    private ArrayList setLocalitiesForTaxon(Taxon taxon, Connection connection) {
*/

    private String getStaticMap(Taxon thisTaxon) {
        s_log.warn("in static map with taxon " + thisTaxon.getName() + " and rank " + thisTaxon.getRank());
        String result = "";
        Map theMap = thisTaxon.getMap();
        float totalLat = 0.0f;
        float totalLon = 0.0f;
        Coordinate coord = null;
        StringBuffer markers = new StringBuffer();
        String preamble = "http://maps.google.com/staticmap?";
        String sizing = "&zoom=5&size=233x233&maptype=terrain&sensor=false&";
        String key = new Utility().getGoogleKey();
        
        
        if (theMap != null && theMap.getPoints() != null && theMap.getPoints().size() > 0) {
            ArrayList points = null;
            if (theMap.getPoints().size() <= MAX_MARKERS) {
                points = theMap.getPoints();
            } else {
                //points = theMap.getCentroidCoords(50);
                points = new ArrayList();
            }
        
            markers.append("markers=");
            Iterator iter = points.iterator();
            while (iter.hasNext()) {
                coord = (Coordinate) iter.next();
                markers.append(Float.valueOf(coord.getLat()).toString());
                markers.append(",");
                markers.append(Float.valueOf(coord.getLon()).toString());
                markers.append(",red");
                if (iter.hasNext()) {
                    markers.append("%7C");
                }
                totalLat += coord.getLat();
                totalLon += coord.getLon();
            }
            String center = "center=" + Float.valueOf(totalLat / points.size()).toString() + "," + Float.valueOf(totalLon / points.size()).toString();
            result = preamble + sizing + center + "&" + markers + "&" + key;
            //s_log.info("static map for " + thisTaxon.getName() + " " + result);
        }
        return result;        
    }


    // Concurrency safe
    public Taxon[] getTaxaArray() {
      // To avoid ConcurrentModificationException
      ArrayList<Taxon> fieldGuideTaxa = getTaxa();
      int childrenCount = fieldGuideTaxa.size();
      Taxon[] fieldGuideTaxaArray = new Taxon[childrenCount];
      fieldGuideTaxa.toArray(fieldGuideTaxaArray);
      return fieldGuideTaxaArray;    
    }

    public ArrayList<Taxon> getTaxa() {
        return taxa;
    }
    public void setTaxa(ArrayList<Taxon> taxa) {
        // A.log("setTaxa() taxa.size:" + taxa.size());
        this.taxa = taxa;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
 
     private String getOverviewTitle(String name) {
	  String title = null;
	  boolean isProject = Project.isProjectName(name);
	   if (isProject) {
		  title = ProjectMgr.getProject(name).getTitle();
	   } else {
 	      //Geolocale geolocale = GeolocaleMgr.getGeolocale(name); 
          //if (geolocale == null) return name;
		  //title = geolocale.getName();
          return name;
	   }
       return title;
    }           
                      
    public void setTitle(String rank, String name) {
        StringBuffer sb = new StringBuffer();

        switch (rank) {
            case "subfamily":
                sb.append("Subfamilies ");
                break;
            case "genus":
                sb.append("Genera ");
                break;
            case "species":
                sb.append("Species ");
                break;
            case "subspecies":
                sb.append("Subspecies ");
                break;
        }
        if (name != null && name.length() > 0) {

          String title = getOverviewTitle(name);
    	   sb.append(" of " + title);
        }
        this.title = sb.toString();        
    }

    public void setTitle(String subfamily, String genus, String species, String name) {
        StringBuffer sb = new StringBuffer();
        Formatter format = new Formatter();
        String rank = "subfamily";
                
        String taxonName = "";
        if (subfamily != null && (genus == null || genus.length() == 0)) {
            taxonName = format.capitalizeFirstLetter(subfamily) + " ";
        }
        if (genus != null) {
            taxonName = format.capitalizeFirstLetter(genus) + " ";
            rank = "genus";
        }

        if (rank.equals("subfamily")) sb.append("for Genera of Subfamily " + taxonName);
        if (rank.equals("genus")) sb.append("for Species of Genus " + taxonName);

        if (name != null && name.length() > 0) {

          String title = getOverviewTitle(name);
          sb.append(" in " + title);
        }
        this.title = sb.toString();        
    }
    
    public Overview getOverview() {
        return overview;
    }
    public void setOverview(Overview overview) {
        this.overview = overview;
    }

    public String getRank() {
        return rank;
    }
    public void setRank(String rank) {
        this.rank = rank;
    }
    
    public Extent getExtent() {
        return extent;
    }
    public void setExtent(Extent extent) {
        this.extent = extent;
    }    
    public void setExtent(String extentStr) {
        if (extentStr == null || "".equals(extentStr)) return;
        Extent extent = new Extent(extentStr);
        s_log.debug("setExtent(" + extent + ") maxLat is " + extent.getMaxLat());
        setExtent(extent);
    }
}



/*
 * Created on Jul 26, 2005
 *
 */
package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import org.apache.commons.lang.StringUtils;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class MapLocality {

    private static Log s_log = LogFactory.getLog(MapLocality.class);
	
	protected String staticMapParams = null;
	protected ArrayList points = new ArrayList();
	protected ArrayList mapLocalities = new ArrayList();

	protected String grain = "";
	protected String searchTerm = "";
	protected String mapName = "";
	protected String googleMapFunction = "";
	   
   	public MapLocality() {
   		super();
   	}
   	
   	/*
   	public MapLocality(String localityCode, Connection connection) {
   		super();
   		
   		//setStaticMapParams(taxon, project);
   	    setPoints(localityCode, connection);

        long thisTime = new GregorianCalendar().getTimeInMillis();
        
        setMapName("map" + thisTime);
		setGoogleMapFunction();
		
   	}
   	*/
   	
   	public MapLocality(ArrayList localityCodes, Connection connection)  {
		super();
		if ((localityCodes != null) && (localityCodes.size() > 0)) {
     
			long thisRand = new Random().nextLong();
            
            setMapName("map" + thisRand);
           
            setPoints(localityCodes,connection);
    		setGoogleMapFunction();
		}
	}
   	
	public String getStaticMapParams() {
		return staticMapParams;
	}
	public void setStaticMapParams(String staticMapParams) {
		this.staticMapParams = staticMapParams;
	}
	
	public void setStaticMapParams(Taxon taxon, String project) {

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
		
		if (Utility.notBlank(project) 
		    && (!project.equals(Project.WORLDANTS))
		    && (!project.equals(Project.ALLANTWEBANTS))
		) {
			terms.add("project+like+%27%25" + project + "%25%27");
		}
		
		String andedTerms = Utility.andify(terms);
		andedTerms = andedTerms.replaceAll(" ","+");
		setStaticMapParams(andedTerms);
		
	}

/*
	public String getMapRoot() {
		return mapRoot;
	}
*/
	public ArrayList getPoints() {
		return points;
	}
	public void setPoints(ArrayList points) {
		this.points = points;
	}
	
	public void setPoints(String granularity, String term, Connection connection) {
		String theQuery;
		ArrayList localities = new ArrayList();
		//if (granularity.equals("bioregion")) {
			theQuery = "select distinct localitycode, decimal_latitude, decimal_longitude, localityname from specimen where " + granularity + "='" + term + "'";
			Statement stmt = null;
			ResultSet rset = null;
			try {
                stmt = DBUtil.getStatement(connection, "setPoints()"); 
				rset = stmt.executeQuery(theQuery);
				float thisLat, thisLon;
				String thisCode, thisName, elevation;
				Locality locality;

				while (rset.next()) {
					thisLat = rset.getFloat("decimal_latitude");
					thisLon = rset.getFloat("decimal_longitude");
					//elevation = rset.getString("elevation");
					thisCode = rset.getString("localitycode");
					thisName = rset.getString("localityname");
					thisName = thisName.replace("'", "");
					thisCode = thisCode.replace("'","");
					thisName = thisName.replace("\"", "");
					thisCode = thisCode.replace("\"","");
					
					if ((thisLon != 0.0) && (thisLat != 0.0)) {

						locality = new Locality();
						locality.setLocalityCode(thisCode);
						locality.setLocalityName(thisName);
						locality.setElevation("");
						points.add(new Coordinate(thisLon, thisLat));
						mapLocalities.add(locality);

					}
				}
			} catch (SQLException e) {
				s_log.error("setPoints() e:" + e);
				org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
			} finally {
              DBUtil.close(stmt, rset, this, "setPoints()");
			} 
	}
	
	public void setPointsWithRestrictions(ArrayList restrictions, Connection connection) {
		String theQuery;
		ArrayList localities = new ArrayList();
		if (restrictions.size() == 0) {
			return;
		}
		//if (granularity.equals("bioregion")) {
			theQuery = "select distinct localitycode, decimal_latitude, decimal_longitude, localityname from specimen where ";
			theQuery += StringUtils.join(restrictions.toArray(), " and ");
			
			Statement stmt = null;
			ResultSet rset = null;
			try {
                stmt = DBUtil.getStatement(connection, "setPointsWithRestrictions()"); 
				rset = stmt.executeQuery(theQuery);
				
				float thisLat, thisLon;
				String thisCode, thisName, elevation;
				Locality locality;

				while (rset.next()) {
					thisLat = rset.getFloat("decimal_latitude");
					thisLon = rset.getFloat("decimal_longitude");
					//elevation = rset.getString("elevation");
					thisCode = rset.getString("localitycode");
					thisName = rset.getString("localityname");
					thisName = thisName.replace("'", "");
					thisCode = thisCode.replace("'","");
					thisName = thisName.replace("\"", "");
					thisCode = thisCode.replace("\"","");
					
					if ((thisLon != 0.0) && (thisLat != 0.0)) {

						locality = new Locality();
						locality.setLocalityCode(thisCode);
						locality.setLocalityName(thisName);
						locality.setElevation("");
						points.add(new Coordinate(thisLon, thisLat));
						mapLocalities.add(locality);

					}
				}
				stmt.close();
				
			} catch (SQLException e) {
				s_log.error("setPointsWithRestrictions() e: " + e);
				org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
			} finally {
              DBUtil.close(stmt, rset, this, "setPointsWithRestrictions()");
			} 
	}
	
	public void setPoints(ArrayList localityCodes, Connection connection) {
		this.points = new ArrayList();
		if (localityCodes.size() > 0) {
			Iterator iter = localityCodes.iterator();
			
			String theQuery = "select decimal_latitude, decimal_longitude, elevation, localitycode, localityname from specimen where ";
			theQuery += " localitycode in ";
			
			
			String thisSpec;
			
			theQuery += "(";
			while (iter.hasNext()) {
				thisSpec = (String) iter.next();
				theQuery += "'" + thisSpec + "'";
				if (iter.hasNext()) {
					theQuery += ",";
				}
			}
			theQuery += ")";
			
			Statement stmt = null;
			ResultSet rset = null;
			try {
                stmt = DBUtil.getStatement(connection, "setPoints(2)"); 
				rset = stmt.executeQuery(theQuery);
				
				float thisLat, thisLon;
				String thisCode, thisName, elevation;
				Locality locality;

				while (rset.next()) {
					thisLat = rset.getFloat("decimal_latitude");
					thisLon = rset.getFloat("decimal_longitude");
					elevation = rset.getString("elevation");
					thisCode = rset.getString("localitycode");
					thisName = rset.getString("localityname");
					
					if ((thisLon != 0.0) && (thisLat != 0.0)) {

						locality = new Locality();
						locality.setLocalityCode(thisCode);
						locality.setLocalityName(thisName);
						locality.setElevation(elevation);
						mapLocalities.add(locality);

					}
				}
			} catch (SQLException e) {
				s_log.error("setPoints() 2 e:" + e);
				org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
			} finally {
              DBUtil.close(stmt, rset, this, "setPoints(2)");
            }			
		}
	}	
	
	/**
	 * @return Returns the googleMapFunction.
	 */
	public String getGoogleMapFunction() {
		return googleMapFunction;
	}
	/**
	 * @param googleMapFunction The googleMapFunction to set.
	 */
	public void setGoogleMapFunction(String googleMapFunction) {
		this.googleMapFunction = googleMapFunction;
	}
	
	/**
	 * Creates a function string to execute in JavaScript
	 * @param googleMapFunction The googleMapFunction to set.
	 */
	public void setGoogleMapFunction() {
		
		StringBuffer theString = null;
		String googleString = null;
		if ((getPoints() != null) && (getPoints().size() > 0)) {
			String latArray = getJavaScriptArray(getPoints(),"lat");
			String lonArray = getJavaScriptArray(getPoints(),"lon");
			if ((latArray != null) && (lonArray != null)) {
			
			  theString = new StringBuffer();
			  if (getPoints().size() > 1) {
				theString.append("drawGoogleMapWithLocalityText(");
				theString.append("'big', ");
				theString.append("'" + getMapName() + "',");
				theString.append(latArray);
				theString.append(",");
				theString.append(lonArray);
				theString.append(",");
				theString.append(getJavaScriptLocalityArray(getMapLocalities(), "localityName"));
				theString.append(",");
				theString.append(getJavaScriptLocalityArray(getMapLocalities(), "localityCode"));
				//theString.append(",");
				//theString.append(getJavaScriptLocalityArray(getMapLocalities(), "elevation"));

				theString.append(");");
			  } else {
			  	theString.append("drawGoogleMapWithLocalityText(");
			  	theString.append("'small', ");
			  	theString.append("'" + getMapName() + "',");
			  	theString.append(((Coordinate) getPoints().get(0)).getLat());
			  	theString.append(",");
			 	theString.append(((Coordinate) getPoints().get(0)).getLon());
				theString.append(",");
				theString.append(getJavaScriptLocalityArray(getMapLocalities(), "localityName"));
				theString.append(",");
				theString.append(getJavaScriptLocalityArray(getMapLocalities(), "localityCode"));
				theString.append(",");
				theString.append(getJavaScriptLocalityArray(getMapLocalities(), "elevation"));
			  	theString.append(");");
			  }
				
			  googleString = theString.toString();
			}
		}
		setGoogleMapFunction(googleString);
	}
	
	public String getJavaScriptArray(ArrayList points, String coord) {
		
		StringBuffer theArrayString = new StringBuffer();
		boolean foundPoint = false;
		theArrayString.append("new Array(");
		
		Iterator theIter = points.iterator();
		Coordinate thisCoord;
		float thisFloat;
		
		while (theIter.hasNext()) {
			thisCoord = (Coordinate) theIter.next();
			if (coord.equals("lat")) {	
				thisFloat = thisCoord.getLat();
			} else {
				thisFloat = thisCoord.getLon();
			}
			if (thisFloat != 0.0) {
				
				if (foundPoint == true) {
					theArrayString.append(",");
				} else {
					foundPoint = true;
				}
				theArrayString.append(new Float(thisFloat));
			}
		}

		theArrayString.append(")");
		
		if (foundPoint == true) {
			return theArrayString.toString();
		} else {
			return null;
		}
	}
	
	public String getJavaScriptLocalityArray(ArrayList localities, String field) {
		
		StringBuffer theArrayString = new StringBuffer();
		boolean foundPoint = false;
		theArrayString.append("new Array(");
		
		Iterator theIter = localities.iterator();
		Locality thisLocality;
		String value="";
		Formatter format = new Formatter();
		
		while (theIter.hasNext()) {
			thisLocality = (Locality) theIter.next();
			if (field.equals("localityName")) {	
				value = format.capitalizeFirstLetter(thisLocality.getLocalityName());
			} else if (field.equals("localityCode")) {
				value = thisLocality.getLocalityCode();
			} else if (field.equals("elevation")) {
				value = thisLocality.getElevation();
			} else {
				value = "";
			}
			
			if (foundPoint == true) {
				theArrayString.append(",");
			} else {
				foundPoint = true;
			}
			theArrayString.append("'" + value + "'");
		}

		theArrayString.append(")");
		
		if (foundPoint == true) {
			return theArrayString.toString();
		} else {
			return null;
		}
	}
	/**
	 * @return Returns the mapName.
	 */
	public String getMapName() {
		return mapName;
	}
	/**
	 * @param mapName The mapName to set.
	 */
	public void setMapName(String mapName) {
		this.mapName = mapName;
	}
		
	public ArrayList getMapLocalities() {
		return mapLocalities;
	}
	/**
	 * @param mapSpecimens The mapSpecimens to set.
	 */
	public void setMapSpecimens(ArrayList mapLocalities) {
		this.mapLocalities = mapLocalities;
	}


	public String getGrain() {
		return grain;
	}


	public void setGrain(String grain) {
		this.grain = grain;
	}


	public String getSearchTerm() {
		return searchTerm;
	}


	public void setSearchTerm(String searchTerm) {
		this.searchTerm = searchTerm;
	}
	
	
}


